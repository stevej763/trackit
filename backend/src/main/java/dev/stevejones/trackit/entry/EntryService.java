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
import java.time.LocalDate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntryService {

    private final EntryRepository entries;
    private final MediaItemRepository mediaItems;
    private final AppUserRepository users;
    private final MetadataProviders providers;

    public EntryService(
            EntryRepository entries,
            MediaItemRepository mediaItems,
            AppUserRepository users,
            MetadataProviders providers) {
        this.entries = entries;
        this.mediaItems = mediaItems;
        this.users = users;
        this.providers = providers;
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

    @Transactional
    public Entry create(Long userId, CreateEntryRequest request) {
        MediaItem mediaItem = resolveMediaItem(request);

        entries.findByUserIdAndMediaItemId(userId, mediaItem.getId()).ifPresent(existing -> {
            throw new ConflictException("You're already tracking " + mediaItem.getTitle());
        });

        AppUser user = users.getReferenceById(userId);
        Entry entry = new Entry(user, mediaItem, request.status());
        if (request.status() == EntryStatus.COMPLETED) {
            entry.setFinishedOn(LocalDate.now());
        }
        return entries.save(entry);
    }

    @Transactional
    public Entry update(Long userId, Long entryId, UpdateEntryRequest request) {
        Entry entry = get(userId, entryId);
        boolean wasAlreadyFinished = entry.getStatus() == EntryStatus.COMPLETED;

        entry.setStatus(request.status());
        entry.setRating(request.rating());
        entry.setReview(normalise(request.review()));
        entry.setStartedOn(request.startedOn());
        entry.setFinishedOn(request.finishedOn());

        // Marking something finished without naming a date means "today": the
        // stats page counts completions by month, so a finished entry with no
        // date would quietly go missing. Only on the transition into COMPLETED,
        // so clearing the date on an already-finished entry still clears it.
        if (request.status() == EntryStatus.COMPLETED
                && request.finishedOn() == null
                && !wasAlreadyFinished) {
            entry.setFinishedOn(LocalDate.now());
        }
        return entry;
    }

    @Transactional
    public Entry updateStatus(Long userId, Long entryId, EntryStatus status) {
        Entry entry = get(userId, entryId);
        entry.setStatus(status);
        // Completing from the grid, where there's nowhere to type a date, fills
        // today in so the stats page has something to count.
        if (status == EntryStatus.COMPLETED && entry.getFinishedOn() == null) {
            entry.setFinishedOn(LocalDate.now());
        }
        return entry;
    }

    @Transactional
    public void delete(Long userId, Long entryId) {
        Entry entry = get(userId, entryId);
        entries.delete(entry);
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
