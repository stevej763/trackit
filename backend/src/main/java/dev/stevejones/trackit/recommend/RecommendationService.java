package dev.stevejones.trackit.recommend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stevejones.trackit.entry.Entry;
import dev.stevejones.trackit.entry.EntryRepository;
import dev.stevejones.trackit.entry.EntryStatus;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import dev.stevejones.trackit.recommend.RecommendationDtos.Recommendation;
import dev.stevejones.trackit.recommend.RecommendationDtos.RecommendationsResponse;
import dev.stevejones.trackit.search.MetadataProviders;
import dev.stevejones.trackit.search.ProviderException;
import dev.stevejones.trackit.search.SearchResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggests titles from the ones the user already rated highly.
 *
 * <p>The actual "which things are alike" judgement is TMDB's and IGDB's, both of
 * which derive it from far more behaviour than a personal library could ever
 * contain. This class picks the seeds, aggregates what comes back, and keeps
 * track of why each suggestion is there.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    /** TMDB needs one request per seed, so this bounds the latency of a cold build. */
    static final int MAX_SEEDS = 10;

    /** Below this, a title is not an endorsement and makes a poor seed. */
    static final int MINIMUM_SEED_RATING = 7;

    static final int MAX_RESULTS = 24;
    static final Duration CACHE_TTL = Duration.ofHours(24);

    private final EntryRepository entries;
    private final RecommendationCacheRepository caches;
    private final MetadataProviders providers;
    private final ObjectMapper objectMapper;

    public RecommendationService(
            EntryRepository entries,
            RecommendationCacheRepository caches,
            MetadataProviders providers,
            ObjectMapper objectMapper) {
        this.entries = entries;
        this.caches = caches;
        this.providers = providers;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecommendationsResponse forUser(Long userId, MediaType mediaType, boolean forceRefresh) {
        List<Entry> seeds = entries.findRecommendationSeeds(
                userId,
                mediaType,
                MINIMUM_SEED_RATING,
                MetadataSource.MANUAL,
                EntryStatus.DROPPED,
                PageRequest.of(0, MAX_SEEDS));

        long ratedCount = entries.countByUserIdAndMediaItemMediaTypeAndRatingIsNotNull(userId, mediaType);
        String fingerprint = fingerprintOf(seeds);
        Optional<RecommendationCache> cached = caches.findByUserIdAndMediaType(userId, mediaType);

        // Read on every request, cached or not: adding a title without scoring it
        // leaves the fingerprint alone, so a cached list can still contain it.
        Set<String> alreadyTracked = new HashSet<>(entries.findAllTrackedExternalIds(userId, mediaType));

        if (!forceRefresh && cached.isPresent() && isFresh(cached.get(), fingerprint)) {
            Optional<List<Recommendation>> items = deserialise(cached.get());
            if (items.isPresent()) {
                return new RecommendationsResponse(
                        mediaType, untracked(items.get(), alreadyTracked), seeds.size(), ratedCount,
                        MINIMUM_SEED_RATING, cached.get().getComputedAt(), true);
            }
        }

        if (seeds.isEmpty()) {
            // Nothing to ask the providers about. Not cached: the answer depends
            // only on the empty seed set, and recomputing it costs one query.
            return new RecommendationsResponse(
                    mediaType, List.of(), 0, ratedCount, MINIMUM_SEED_RATING, Instant.now(), false);
        }

        List<Recommendation> items;
        try {
            items = compute(mediaType, seeds, alreadyTracked);
        } catch (ProviderException ex) {
            // A stale list beats an error page, so serve the last good one if
            // there is one and let the caller see how old it is.
            Optional<List<Recommendation>> fallback = cached.flatMap(this::deserialise);
            if (fallback.isPresent()) {
                log.warn("Serving cached recommendations; provider unavailable: {}", ex.getMessage());
                return new RecommendationsResponse(
                        mediaType, untracked(fallback.get(), alreadyTracked), seeds.size(), ratedCount,
                        MINIMUM_SEED_RATING, cached.get().getComputedAt(), true);
            }
            throw ex;
        }

        store(userId, mediaType, fingerprint, items, cached);
        return new RecommendationsResponse(
                mediaType, items, seeds.size(), ratedCount, MINIMUM_SEED_RATING, Instant.now(), false);
    }

    private List<Recommendation> compute(MediaType mediaType, List<Entry> seeds, Set<String> alreadyTracked) {
        Map<String, Entry> seedsByExternalId = new LinkedHashMap<>();
        for (Entry seed : seeds) {
            seedsByExternalId.put(seed.getMediaItem().getExternalId(), seed);
        }

        Map<String, List<SearchResult>> bySeed = providers
                .forType(mediaType)
                .recommendationsFor(mediaType, List.copyOf(seedsByExternalId.keySet()));

        Map<String, Candidate> candidates = new LinkedHashMap<>();

        bySeed.forEach((seedExternalId, results) -> {
            Entry seed = seedsByExternalId.get(seedExternalId);
            if (seed == null) {
                // A provider returned a seed we didn't ask about; ignore it.
                return;
            }
            for (int rank = 0; rank < results.size(); rank++) {
                SearchResult result = results.get(rank);
                if (alreadyTracked.contains(result.externalId())) {
                    continue;
                }
                candidates
                        .computeIfAbsent(result.externalId(), key -> new Candidate(result))
                        .pointedAtBy(seed, rank);
            }
        });

        return candidates.values().stream()
                .sorted(Candidate.BEST_FIRST)
                .limit(MAX_RESULTS)
                .map(Candidate::toRecommendation)
                .toList();
    }

    private static List<Recommendation> untracked(List<Recommendation> items, Set<String> alreadyTracked) {
        return items.stream().filter(item -> !alreadyTracked.contains(item.externalId())).toList();
    }

    private void store(
            Long userId,
            MediaType mediaType,
            String fingerprint,
            List<Recommendation> items,
            Optional<RecommendationCache> existing) {

        String payload;
        try {
            payload = objectMapper.writeValueAsString(items);
        } catch (Exception ex) {
            // Not worth failing the request over: the user still gets their list.
            log.warn("Could not cache recommendations", ex);
            return;
        }

        existing.ifPresentOrElse(
                cache -> cache.replaceWith(fingerprint, payload),
                () -> caches.save(new RecommendationCache(userId, mediaType, fingerprint, payload)));
    }

    private boolean isFresh(RecommendationCache cache, String fingerprint) {
        return cache.getSeedFingerprint().equals(fingerprint)
                && cache.getComputedAt().isAfter(Instant.now().minus(CACHE_TTL));
    }

    private Optional<List<Recommendation>> deserialise(RecommendationCache cache) {
        try {
            return Optional.of(objectMapper.readValue(cache.getPayload(), new TypeReference<>() {}));
        } catch (Exception ex) {
            // The record shape changed under an old payload. Treat it as a miss
            // rather than failing every request until the row is cleared.
            log.warn("Discarding unreadable cached recommendations (cache row {})", cache.getId());
            return Optional.empty();
        }
    }

    /**
     * Identifies the seed set by id and score, so scoring something new (or
     * changing a score) invalidates the cache at once instead of leaving the
     * user waiting out {@link #CACHE_TTL}.
     */
    private static String fingerprintOf(List<Entry> seeds) {
        StringBuilder raw = new StringBuilder();
        for (Entry seed : seeds) {
            raw.append(seed.getMediaItem().getExternalId()).append(':').append(seed.getRating()).append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by every JVM", ex);
        }
    }

    /** One suggested title, accumulating the seeds that pointed at it. */
    private static final class Candidate {

        /**
         * Consensus first: a title two of your favourites point at outranks one
         * a single favourite points at, even a 10. Then the number of seeds.
         *
         * <p>Then the provider's own position, which matters more than it looks:
         * seeds share a score often enough that most of a page is one big tie,
         * and TMDB and IGDB both return their results most-relevant-first.
         * Without this the tie fell through to the title and a page of
         * suggestions came out in alphabetical order, which reads as broken.
         * Title remains the last resort so identical requests stay stable.
         */
        static final Comparator<Candidate> BEST_FIRST = Comparator
                .comparingInt((Candidate candidate) -> candidate.score).reversed()
                .thenComparing(Comparator.comparingInt((Candidate candidate) -> candidate.matches).reversed())
                .thenComparingInt(candidate -> candidate.bestRank)
                .thenComparing(candidate -> candidate.result.title());

        private final SearchResult result;
        private final List<Entry> seeds = new ArrayList<>();
        private int score;
        private int matches;
        private int bestRank = Integer.MAX_VALUE;
        private Entry bestSeed;

        Candidate(SearchResult result) {
            this.result = result;
        }

        /**
         * @param rank this title's position in that seed's result list, where 0
         *             is the provider's strongest match
         */
        void pointedAtBy(Entry seed, int rank) {
            if (seeds.contains(seed)) {
                return;
            }
            seeds.add(seed);
            score += seed.getRating();
            matches++;
            bestRank = Math.min(bestRank, rank);
            if (bestSeed == null || seed.getRating() > bestSeed.getRating()) {
                bestSeed = seed;
            }
        }

        Recommendation toRecommendation() {
            return new Recommendation(
                    result.source(),
                    result.mediaType(),
                    result.externalId(),
                    result.title(),
                    result.releaseYear(),
                    result.overview(),
                    result.posterUrl(),
                    bestSeed.getMediaItem().getTitle(),
                    bestSeed.getRating(),
                    matches);
        }
    }
}
