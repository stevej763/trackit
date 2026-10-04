package dev.stevejones.trackit.stats;

import dev.stevejones.trackit.stats.StatsResponse.Bucket;
import dev.stevejones.trackit.stats.StatsResponse.FilmTime;
import dev.stevejones.trackit.stats.StatsResponse.Genre;
import java.util.List;

/**
 * One calendar year of finishes, in the user's zone. Gap-filled like
 * {@link StatsResponse}: every media type, and January to December.
 *
 * @param years     every year with a finish, newest first, plus this one, so
 *                  the page can step between them without a second request
 * @param best      the year's highest-scored finishes
 */
public record YearInReview(
        int year,
        List<Integer> years,
        long finished,
        Double averageRating,
        List<Bucket> byMediaType,
        List<Bucket> byMonth,
        FilmTime filmTime,
        List<Genre> genres,
        List<Highlight> best) {

    public record Highlight(long entryId, String title, String mediaType, String posterUrl, int rating) {
    }
}
