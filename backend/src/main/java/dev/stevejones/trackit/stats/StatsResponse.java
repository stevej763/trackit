package dev.stevejones.trackit.stats;

import java.util.List;

/**
 * Everything the stats page draws. Buckets are gap-filled server-side (every
 * media type, every status, ratings 1-10, twelve months) so the charts can map
 * straight over the arrays without inventing missing points.
 */
public record StatsResponse(
        long totalItems,
        long ratedItems,
        Double averageRating,
        List<Bucket> byMediaType,
        List<Bucket> byStatus,
        List<Bucket> ratingHistogram,
        List<Bucket> finishedByMonth,
        List<Average> averageRatingByMediaType,
        FilmTime filmTime,
        List<Genre> genres,
        List<Integer> years) {

    /**
     * @param key   enum name, rating as a string, or an ISO year-month
     * @param label human-readable form for axis ticks
     */
    public record Bucket(String key, String label, long count) {
    }

    public record Average(String key, String label, Double average) {
    }

    /**
     * Running time of finished films. Films only: TMDB gives TV no reliable
     * episode length and IGDB gives games no playtime, so anything wider would
     * be a guess dressed up as a number.
     *
     * @param minutes             summed running time
     * @param filmsWithoutRuntime finished films that don't say how long they
     *                            are (mostly hand-typed), so the total is short
     */
    public record FilmTime(long minutes, long filmsWithoutRuntime) {
    }

    /**
     * @param count   titles carrying this genre; one title can carry several
     * @param average mean score of the scored ones, null if none are
     */
    public record Genre(String name, long count, Double average) {
    }
}
