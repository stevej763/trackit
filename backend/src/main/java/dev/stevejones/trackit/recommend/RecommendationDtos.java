package dev.stevejones.trackit.recommend;

import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import java.time.Instant;
import java.util.List;

/** Request and response bodies for /api/recommendations. */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    /**
     * One suggestion, carrying the reason it is here.
     *
     * @param becauseOfTitle  the best-scored seed that pointed at this title
     * @param becauseOfRating what the user gave that seed
     * @param seedMatches     how many of the user's seeds pointed here; more is
     *                        a stronger signal, and the UI says so
     */
    public record Recommendation(
            MetadataSource source,
            MediaType mediaType,
            String externalId,
            String title,
            Integer releaseYear,
            String overview,
            String posterUrl,
            String becauseOfTitle,
            Integer becauseOfRating,
            int seedMatches) {
    }

    /**
     * @param seedCount          provider-backed, well-scored titles the
     *                           suggestions were built from
     * @param ratedCount         scored titles of this type however they were
     *                           added, so the UI can tell "score something
     *                           first" apart from "everything you scored was
     *                           typed in by hand"
     * @param minimumSeedRating  the score a title needs to become a seed,
     *                           returned rather than duplicated in the client so
     *                           the two can't drift apart
     */
    public record RecommendationsResponse(
            MediaType mediaType,
            List<Recommendation> items,
            int seedCount,
            long ratedCount,
            int minimumSeedRating,
            Instant computedAt,
            boolean fromCache) {
    }
}
