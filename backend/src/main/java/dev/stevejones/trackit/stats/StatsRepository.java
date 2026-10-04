package dev.stevejones.trackit.stats;

import java.time.Instant;
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

    /**
     * Running time of every finished film. TV and games carry no running time,
     * so they can't be counted honestly; films without one (hand-typed, mostly)
     * are counted separately so the page can say the total is short.
     */
    @Query(value = """
            select coalesce(sum(mi.runtime_minutes), 0) as minutes,
                   count(*) filter (where mi.runtime_minutes is null) as missing
            from entry e
            join media_item mi on mi.id = e.media_item_id
            where e.user_id = :userId
              and mi.media_type = 'MOVIE'
              and e.finished_at is not null
            """, nativeQuery = true)
    RuntimeTotal finishedFilmRuntime(@Param("userId") Long userId);

    /** The library's genres, most-tracked first, each with its average score. */
    @Query(value = """
            select g.genre as key, count(*) as total, avg(e.rating) as average
            from entry e
            join media_item mi on mi.id = e.media_item_id
            cross join lateral jsonb_array_elements_text(mi.genres) as g(genre)
            where e.user_id = :userId
            group by g.genre
            order by total desc, g.genre
            limit :limit
            """, nativeQuery = true)
    List<GenreRow> genres(@Param("userId") Long userId, @Param("limit") int limit);

    /** Calendar years, in the given zone, in which anything was finished. Newest first. */
    @Query(value = """
            select distinct cast(extract(year from e.finished_at at time zone :zone) as int)
            from entry e
            where e.user_id = :userId and e.finished_at is not null
            order by 1 desc
            """, nativeQuery = true)
    List<Integer> finishedYears(@Param("userId") Long userId, @Param("zone") String zone);

    // A year in review. Each takes the year as an instant range [from, to),
    // worked out in the user's zone, so the index on finished_at applies.

    @Query(value = """
            select mi.media_type as key, count(*) as total
            from entry e
            join media_item mi on mi.id = e.media_item_id
            where e.user_id = :userId and e.finished_at >= :from and e.finished_at < :to
            group by mi.media_type
            """, nativeQuery = true)
    List<KeyCount> countFinishedByMediaTypeBetween(
            @Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            select to_char(e.finished_at at time zone :zone, 'YYYY-MM') as key, count(*) as total
            from entry e
            where e.user_id = :userId and e.finished_at >= :from and e.finished_at < :to
            group by 1
            """, nativeQuery = true)
    List<KeyCount> countFinishedByMonthBetween(
            @Param("userId") Long userId,
            @Param("zone") String zone,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(value = """
            select avg(e.rating) as average
            from entry e
            where e.user_id = :userId and e.finished_at >= :from and e.finished_at < :to
              and e.rating is not null
            """, nativeQuery = true)
    Double averageRatingFinishedBetween(
            @Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            select coalesce(sum(mi.runtime_minutes), 0) as minutes,
                   count(*) filter (where mi.runtime_minutes is null) as missing
            from entry e
            join media_item mi on mi.id = e.media_item_id
            where e.user_id = :userId
              and mi.media_type = 'MOVIE'
              and e.finished_at >= :from and e.finished_at < :to
            """, nativeQuery = true)
    RuntimeTotal finishedFilmRuntimeBetween(
            @Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
            select g.genre as key, count(*) as total, avg(e.rating) as average
            from entry e
            join media_item mi on mi.id = e.media_item_id
            cross join lateral jsonb_array_elements_text(mi.genres) as g(genre)
            where e.user_id = :userId and e.finished_at >= :from and e.finished_at < :to
            group by g.genre
            order by total desc, g.genre
            limit :limit
            """, nativeQuery = true)
    List<GenreRow> genresFinishedBetween(
            @Param("userId") Long userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("limit") int limit);

    /** The year's best: highest score first, then the most recently finished. */
    @Query(value = """
            select e.id as "entryId", mi.title as title, mi.media_type as "mediaType",
                   mi.poster_url as "posterUrl", e.rating as rating
            from entry e
            join media_item mi on mi.id = e.media_item_id
            where e.user_id = :userId and e.finished_at >= :from and e.finished_at < :to
              and e.rating is not null
            order by e.rating desc, e.finished_at desc
            limit :limit
            """, nativeQuery = true)
    List<HighlightRow> bestFinishedBetween(
            @Param("userId") Long userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("limit") int limit);

    interface KeyCount {
        String getKey();

        long getTotal();
    }

    interface KeyAverage {
        String getKey();

        Double getAverage();
    }

    interface RuntimeTotal {
        long getMinutes();

        long getMissing();
    }

    interface GenreRow {
        String getKey();

        long getTotal();

        Double getAverage();
    }

    interface HighlightRow {
        Long getEntryId();

        String getTitle();

        String getMediaType();

        String getPosterUrl();

        Integer getRating();
    }
}
