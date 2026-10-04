package dev.stevejones.trackit.search;

import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaItemRepository;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Re-fetches catalogue rows from their provider, so a show's season count and
 * an upcoming film's release year don't stay as they were on the day someone
 * first added them.
 *
 * <p>Two ways in: the item page's "refresh details" action, and a nightly job
 * for rows likely to have moved on. Either way the provider is called with no
 * transaction open, and the update gets a short one of its own.
 */
@Service
public class CatalogueRefresher {

    private static final Logger log = LoggerFactory.getLogger(CatalogueRefresher.class);

    /** A refresh this soon after the last fetch would almost certainly change nothing. */
    static final Duration COOLDOWN = Duration.ofMinutes(10);

    /** How old a row's details must be before the nightly job looks at it again. */
    static final Duration STALE_AFTER = Duration.ofDays(7);

    /** Per night. Bounds the provider traffic however big the catalogue gets. */
    static final int BATCH_SIZE = 50;

    /**
     * Failures in a row after which the job assumes that type's provider is
     * down or unconfigured and leaves its titles until tomorrow.
     */
    private static final int GIVE_UP_AFTER = 3;

    private final MediaItemRepository mediaItems;
    private final MetadataProviders providers;
    private final TransactionTemplate writes;
    private final Clock clock;

    public CatalogueRefresher(
            MediaItemRepository mediaItems, MetadataProviders providers, PlatformTransactionManager transactions) {
        this.mediaItems = mediaItems;
        this.providers = providers;
        this.writes = new TransactionTemplate(transactions);
        this.clock = Clock.systemUTC();
    }

    /**
     * Fetches this title's details again and stores them, unless it was
     * fetched within {@link #COOLDOWN}, which keeps a repeatedly pressed button
     * from becoming a stream of provider calls.
     *
     * @return whether a fetch was made
     * @throws ProviderException if the provider can't be reached, or the item
     *                           was typed in by hand and has none
     */
    public boolean refresh(MediaItem item) {
        if (item.getSource() == MetadataSource.MANUAL) {
            throw new IllegalArgumentException("Hand-typed titles have no provider to refresh from");
        }
        Instant fetchedAt = item.getMetadataFetchedAt();
        if (fetchedAt != null && fetchedAt.isAfter(clock.instant().minus(COOLDOWN))) {
            return false;
        }

        MediaItem fetched = providers.forType(item.getMediaType())
                .fetchDetails(item.getMediaType(), item.getExternalId());
        writes.executeWithoutResult(status -> mediaItems.findById(item.getId())
                .ifPresent(stored -> stored.refreshFrom(fetched)));
        return true;
    }

    /**
     * The nightly job. Off with {@code TRACKIT_METADATA_REFRESH_CRON=-}.
     *
     * <p>Gives up on a media type after a few failures in a row, so a provider
     * that's down (or has no key, which fails without making a request) costs
     * little and doesn't stop the other provider's titles. A title the provider
     * no longer has is marked as checked, keeping its old details, or it would
     * head the queue every night.
     */
    @Scheduled(cron = "${trackit.metadata.refresh-cron}", zone = "UTC")
    public void refreshStale() {
        Instant now = clock.instant();
        List<MediaItem> due = mediaItems.findDueForRefresh(
                now.minus(STALE_AFTER),
                now.atZone(ZoneOffset.UTC).getYear() - 1,
                PageRequest.of(0, BATCH_SIZE));

        int refreshed = 0;
        Map<MediaType, Integer> failuresInARow = new EnumMap<>(MediaType.class);
        for (MediaItem item : due) {
            MediaType type = item.getMediaType();
            if (failuresInARow.getOrDefault(type, 0) >= GIVE_UP_AFTER) {
                continue;
            }
            try {
                refresh(item);
                refreshed++;
                failuresInARow.put(type, 0);
            } catch (UnknownTitleException ex) {
                log.info("{} no longer has {} {}; keeping its old details", item.getSource(), type, item.getExternalId());
                writes.executeWithoutResult(status -> mediaItems.findById(item.getId())
                        .ifPresent(stored -> stored.setMetadataFetchedAt(now)));
            } catch (ProviderException ex) {
                int failures = failuresInARow.merge(type, 1, Integer::sum);
                log.debug("Couldn't refresh {} {} {}: {}", item.getSource(), type, item.getExternalId(), ex.getMessage());
                if (failures == GIVE_UP_AFTER) {
                    log.info("Leaving {} details until next time: {}", type, ex.getMessage());
                }
            }
        }
        if (refreshed > 0) {
            log.info("Refreshed details for {} of {} catalogue titles", refreshed, due.size());
        }
    }
}
