package dev.stevejones.trackit.entry;

import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<Entry, Long>, JpaSpecificationExecutor<Entry> {

    /** Scoped by user id, so another user's entry simply isn't found. */
    @Query("select e from Entry e join fetch e.mediaItem where e.id = :id and e.user.id = :userId")
    Optional<Entry> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByUserIdAndMediaItemId(Long userId, Long mediaItemId);

    Optional<Entry> findByUserIdAndMediaItemId(Long userId, Long mediaItemId);

    /**
     * Which of these provider ids the user already tracks, so search results can
     * be labelled instead of offering a duplicate add.
     */
    @Query("""
            select mi.externalId as externalId, e.id as entryId
            from Entry e
            join e.mediaItem mi
            where e.user.id = :userId
              and mi.source = :source
              and mi.mediaType = :mediaType
              and mi.externalId in :externalIds
            """)
    List<TrackedExternalId> findTrackedExternalIds(
            @Param("userId") Long userId,
            @Param("source") MetadataSource source,
            @Param("mediaType") MediaType mediaType,
            @Param("externalIds") List<String> externalIds);

    /** Projection for {@link #findTrackedExternalIds}. */
    interface TrackedExternalId {
        String getExternalId();

        Long getEntryId();
    }
}
