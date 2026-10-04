package dev.stevejones.trackit.stats;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import dev.stevejones.trackit.entry.Entry;

/**
 * Aggregates for the stats page. Native SQL here because the month bucketing
 * and gap-free grouping are far clearer in Postgres than in JPQL.
 */
public interface StatsRepository extends Repository<Entry, Long> {

    @Query(value = """
            select mi.media_type as key, count(*) as total
            from entry e
            join media_item mi on mi.id = e.media_item_id
            where e.user_id = :userId
            group by mi.media_type
            """, nativeQuery = true)
    List<KeyCount> countByMediaType(@Param("userId") Long userId);

    @Query(value = """
            select e.status as key, count(*) as total
            from entry e
            where e.user_id = :userId
            group by e.status
            """, nativeQuery = true)
    List<KeyCount> countByStatus(@Param("userId") Long userId);

    @Query(value = """
            select e.rating::text as key, count(*) as total
            from entry e
            where e.user_id = :userId and e.rating is not null
            group by e.rating
            """, nativeQuery = true)
    List<KeyCount> countByRating(@Param("userId") Long userId);

    /**
     * Completions per calendar month in the given zone, for the last 12 months
     * including this one. {@code timestamptz AT TIME ZONE} gives the wall-clock
     * time there; applied to a plain timestamp it converts back to an instant.
     */
    @Query(value = """
            select to_char(date_trunc('month', e.finished_at at time zone :zone), 'YYYY-MM') as key,
                   count(*) as total
            from entry e
            where e.user_id = :userId
              and e.finished_at is not null
              and e.finished_at >= (date_trunc('month', now() at time zone :zone) - interval '11 months')
                                   at time zone :zone
            group by 1
            """, nativeQuery = true)
    List<KeyCount> countFinishedByMonth(@Param("userId") Long userId, @Param("zone") String zone);

    @Query(value = """
            select mi.media_type as key, avg(e.rating) as average
            from entry e
            join media_item mi on mi.id = e.media_item_id
            where e.user_id = :userId and e.rating is not null
            group by mi.media_type
            """, nativeQuery = true)
    List<KeyAverage> averageRatingByMediaType(@Param("userId") Long userId);

    interface KeyCount {
        String getKey();

        long getTotal();
    }

    interface KeyAverage {
        String getKey();

        Double getAverage();
    }
}
