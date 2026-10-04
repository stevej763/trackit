package dev.stevejones.trackit.entry;

import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<Entry, Long>, JpaSpecificationExecutor<Entry> {

    /** Scoped by user id, so another user's entry simply isn't found. */
    @Query("select e from Entry e join fetch e.mediaItem where e.id = :id and e.user.id = :userId")
    Optional<Entry> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByUserIdAndMediaItemId(Long userId, Long mediaItemId);

    /** The whole library, oldest first, for an export. */
    @Query("select e from Entry e join fetch e.mediaItem where e.user.id = :userId order by e.createdAt, e.id")
    List<Entry> findAllForExport(@Param("userId") Long userId);

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

    /**
     * The entries recommendations are built from: provider-backed titles of this
     * type that the user scored well and didn't give up on, best first.
     *
     * <p>Manual entries can't be seeds - there is no provider id to ask about -
     * which is why callers report the seed count back to the user.
     */
    @Query("""
            select e from Entry e
            join fetch e.mediaItem mi
            where e.user.id = :userId
              and mi.mediaType = :mediaType
              and mi.externalId is not null
              and mi.source <> :manual
              and e.rating >= :minimumRating
              and e.status <> :dropped
            order by e.rating desc, e.updatedAt desc
            """)
    List<Entry> findRecommendationSeeds(
            @Param("userId") Long userId,
            @Param("mediaType") MediaType mediaType,
            @Param("minimumRating") int minimumRating,
            @Param("manual") MetadataSource manual,
            @Param("dropped") EntryStatus dropped,
            Pageable pageable);

    /**
     * Everything of this type the user already tracks, to keep it out of
     * suggestions. Not filtered by source: exactly one provider serves each
     * media type (see {@code MetadataProviders#forType}), so provider ids cannot
     * collide within one type.
     */
    @Query("""
            select mi.externalId from Entry e
            join e.mediaItem mi
            where e.user.id = :userId
              and mi.mediaType = :mediaType
              and mi.externalId is not null
            """)
    List<String> findAllTrackedExternalIds(
            @Param("userId") Long userId,
            @Param("mediaType") MediaType mediaType);

    /** How many of this type have a score at all, however they were added. */
    long countByUserIdAndMediaItemMediaTypeAndRatingIsNotNull(Long userId, MediaType mediaType);

    /** Projection for {@link #findTrackedExternalIds}. */
    interface TrackedExternalId {
        String getExternalId();

        Long getEntryId();
    }
}
