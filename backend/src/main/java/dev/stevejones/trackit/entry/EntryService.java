package dev.stevejones.trackit.entry;

import dev.stevejones.trackit.auth.AppUser;
import dev.stevejones.trackit.auth.AppUserRepository;
import dev.stevejones.trackit.common.BadRequestException;
import dev.stevejones.trackit.common.ConflictException;
import dev.stevejones.trackit.common.NotFoundException;
import dev.stevejones.trackit.entry.EntryDtos.CreateEntryRequest;
import dev.stevejones.trackit.entry.EntryDtos.ManualItem;
import dev.stevejones.trackit.entry.EntryDtos.UpdateEntryRequest;
import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaItemRepository;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import dev.stevejones.trackit.search.MetadataProviders;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EntryService {

    private final EntryRepository entries;
    private final MediaItemRepository mediaItems;
    private final AppUserRepository users;
    private final MetadataProviders providers;
    private final TransactionTemplate transactions;

    public EntryService(
            EntryRepository entries,
            MediaItemRepository mediaItems,
            AppUserRepository users,
            MetadataProviders providers,
            TransactionTemplate transactions) {
        this.entries = entries;
        this.mediaItems = mediaItems;
        this.users = users;
        this.providers = providers;
        this.transactions = transactions;
    }

    /**
     * The library view. The page request is deliberately unsorted: ordering is
     * applied inside the specification so it can reuse the media-item fetch join
     * and push nulls last (see
     * {@link EntrySpecifications#withMediaItemOrderedBy}).
     */
    @Transactional(readOnly = true)
    public Page<Entry> list(
            Long userId,
            MediaType mediaType,
            EntryStatus status,
            Integer minRating,
            String query,
            String sortProperty,
            Sort.Direction direction,
            int page,
            int size) {

        Specification<Entry> spec = EntrySpecifications.ownedBy(userId)
                .and(EntrySpecifications.withMediaItemOrderedBy(sortProperty, direction))
                .and(EntrySpecifications.hasType(mediaType))
                .and(EntrySpecifications.hasStatus(status))
                .and(EntrySpecifications.ratedAtLeast(minRating))
                .and(EntrySpecifications.titleContains(query));

        return entries.findAll(spec, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Entry get(Long userId, Long entryId) {
        return entries.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new NotFoundException("No such entry"));
    }

    /**
     * Deliberately not one transaction. Resolving the catalogue row may call a
     * provider, which shouldn't hold a connection open, and each insert can lose
     * a race to a concurrent request (a double-clicked Add). Postgres aborts a
     * transaction on a constraint violation, so recovering from one with a
     * follow-up query only works if that query runs in a transaction of its own.
     */
    public Entry create(Long userId, CreateEntryRequest request) {
        MediaItem mediaItem = resolveMediaItem(request);
        try {
            return transactions.execute(status -> insertEntry(userId, mediaItem, request.status()));
        } catch (DataIntegrityViolationException ex) {
            // The unique (user, media item) constraint is the real arbiter.
            throw alreadyTracking(mediaItem);
        }
    }

    private Entry insertEntry(Long userId, MediaItem mediaItem, EntryStatus status) {
        if (entries.existsByUserIdAndMediaItemId(userId, mediaItem.getId())) {
            throw alreadyTracking(mediaItem);
        }

        AppUser user = users.getReferenceById(userId);
        Entry entry = new Entry(user, mediaItem, status);
        if (status == EntryStatus.COMPLETED) {
            entry.setFinishedAt(Instant.now());
        }
        return entries.saveAndFlush(entry);
    }

    private static ConflictException alreadyTracking(MediaItem mediaItem) {
        return new ConflictException("You're already tracking " + mediaItem.getTitle());
    }

    @Transactional
    public Entry update(Long userId, Long entryId, UpdateEntryRequest request) {
        Entry entry = get(userId, entryId);
        boolean wasAlreadyFinished = entry.getStatus() == EntryStatus.COMPLETED;

        entry.setStatus(request.status());
        entry.setRating(request.rating());
        entry.setReview(normalise(request.review()));
        entry.setStartedOn(request.startedOn());
        entry.setFinishedAt(request.finishedAt());

        // Marking something finished without naming a date means "now": the
        // stats page counts completions by month, so a finished entry with no
        // date would quietly go missing. Only on the transition into COMPLETED,
        // so clearing the date on an already-finished entry still clears it.
        // An instant, not a date, so no server time zone decides which day.
        if (request.status() == EntryStatus.COMPLETED
                && request.finishedAt() == null
                && !wasAlreadyFinished) {
            entry.setFinishedAt(Instant.now());
        }
        return entry;
    }

    @Transactional
    public Entry updateStatus(Long userId, Long entryId, EntryStatus status) {
        Entry entry = get(userId, entryId);
        entry.setStatus(status);
        // Completing from the grid, where there's nowhere to type a date,
        // records the moment so the stats page has something to count.
        if (status == EntryStatus.COMPLETED && entry.getFinishedAt() == null) {
            entry.setFinishedAt(Instant.now());
        }
        return entry;
    }

    @Transactional
    public void delete(Long userId, Long entryId) {
        Entry entry = get(userId, entryId);
        entries.delete(entry);
        // A hand-typed title belongs to the one entry that created it; nothing
        // else can ever point at it. Provider titles stay for other users.
        if (entry.getMediaItem().getSource() == MetadataSource.MANUAL) {
            mediaItems.delete(entry.getMediaItem());
        }
    }

    /**
     * Finds or creates the shared catalogue row this entry points at: a cached
     * provider item, a fresh provider fetch, or a hand-typed one.
     */
    private MediaItem resolveMediaItem(CreateEntryRequest request) {
        if (request.manual() != null) {
            return mediaItems.save(manualMediaItem(request.mediaType(), request.manual()));
        }

        if (request.source() == null || request.externalId() == null || request.externalId().isBlank()) {
            throw new BadRequestException(
                    "Provide either a provider result (source and externalId) or a manual item");
        }
        if (request.source() == MetadataSource.MANUAL) {
            throw new BadRequestException("A manual item needs its details in the 'manual' field");
        }

        return mediaItems
                .findBySourceAndMediaTypeAndExternalId(
                        request.source(), request.mediaType(), request.externalId())
                .orElseGet(() -> fetchAndSave(request));
    }

    private MediaItem fetchAndSave(CreateEntryRequest request) {
        MediaItem fetched = providers.forType(request.mediaType())
                .fetchDetails(request.mediaType(), request.externalId());
        try {
            // Its own transaction (create() has none), so a lost race leaves
            // nothing aborted for the lookup below.
            return mediaItems.saveAndFlush(fetched);
        } catch (DataIntegrityViolationException ex) {
            // Another request inserted the same title first; use theirs.
            return mediaItems
                    .findBySourceAndMediaTypeAndExternalId(
                            request.source(), request.mediaType(), request.externalId())
                    .orElseThrow(() -> ex);
        }
    }

    private static MediaItem manualMediaItem(MediaType mediaType, ManualItem manual) {
        MediaItem item = new MediaItem();
        item.setSource(MetadataSource.MANUAL);
        item.setMediaType(mediaType);
        item.setTitle(manual.title().trim());
        item.setReleaseYear(manual.releaseYear());
        item.setOverview(normalise(manual.overview()));
        item.setPosterUrl(normalise(manual.posterUrl()));
        item.setBackdropUrl(normalise(manual.backdropUrl()));
        item.setRuntimeMinutes(manual.runtimeMinutes());
        item.setSeasonCount(manual.seasonCount());
        item.setEpisodeCount(manual.episodeCount());
        item.setPlatforms(manual.platforms());
        item.setGenres(manual.genres());
        item.setMetadataFetchedAt(Instant.now());
        return item;
    }

    /** Treats blank input as "no value", so empty strings don't fill the table. */
    private static String normalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
