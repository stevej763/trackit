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
        List<Average> averageRatingByMediaType) {

    /**
     * @param key   enum name, rating as a string, or an ISO year-month
     * @param label human-readable form for axis ticks
     */
    public record Bucket(String key, String label, long count) {
    }

    public record Average(String key, String label, Double average) {
    }
}
