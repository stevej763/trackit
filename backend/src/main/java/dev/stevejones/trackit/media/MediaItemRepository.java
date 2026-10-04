package dev.stevejones.trackit.media;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    Optional<MediaItem> findBySourceAndMediaTypeAndExternalId(
            MetadataSource source, MediaType mediaType, String externalId);

    /**
     * Hand-typed titles belong to the one entry that created them, so they go
     * when their owner's account does. Deleting them cascades to that entry.
     */
    @Modifying
    @Query("""
            delete from MediaItem mi
            where mi.source = dev.stevejones.trackit.media.MetadataSource.MANUAL
              and mi.id in (select e.mediaItem.id from Entry e where e.user.id = :userId)
            """)
    void deleteManualItemsTrackedBy(@Param("userId") Long userId);

    /**
     * Provider titles someone still tracks whose details may have moved on
     * since they were fetched: shows (new seasons) and anything recent or
     * undated (release dates firm up, artwork arrives). Oldest fetch first.
     */
    @Query("""
            select mi from MediaItem mi
            where mi.source <> dev.stevejones.trackit.media.MetadataSource.MANUAL
              and (mi.metadataFetchedAt is null or mi.metadataFetchedAt < :fetchedBefore)
              and (mi.mediaType = dev.stevejones.trackit.media.MediaType.TV
                   or mi.releaseYear is null
                   or mi.releaseYear >= :releasedFrom)
              and exists (select 1 from Entry e where e.mediaItem = mi)
            order by mi.metadataFetchedAt asc nulls first
            """)
    List<MediaItem> findDueForRefresh(
            @Param("fetchedBefore") Instant fetchedBefore,
            @Param("releasedFrom") int releasedFrom,
            Pageable pageable);
}
