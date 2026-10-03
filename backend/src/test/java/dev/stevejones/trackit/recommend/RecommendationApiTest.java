package dev.stevejones.trackit.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.stevejones.trackit.IntegrationTest;
import dev.stevejones.trackit.auth.AppUser;
import dev.stevejones.trackit.auth.AppUserPrincipal;
import dev.stevejones.trackit.auth.AppUserRepository;
import dev.stevejones.trackit.entry.Entry;
import dev.stevejones.trackit.entry.EntryRepository;
import dev.stevejones.trackit.entry.EntryStatus;
import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaItemRepository;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import dev.stevejones.trackit.search.MetadataProvider;
import dev.stevejones.trackit.search.MetadataProviders;
import dev.stevejones.trackit.search.ProviderException;
import dev.stevejones.trackit.search.SearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The scoring, exclusion and caching behaviour. The providers are stubbed here
 * on purpose: what TMDB and IGDB actually return is covered by the provider
 * tests, and what matters at this level is what we do with the answer.
 */
class RecommendationApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired EntryRepository entries;
    @Autowired MediaItemRepository mediaItems;
    @Autowired RecommendationCacheRepository caches;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean MetadataProviders providers;

    private MetadataProvider provider;
    private AppUser steveUser;
    private AppUserPrincipal steve;
    private AppUserPrincipal anna;

    @BeforeEach
    void setUp() {
        caches.deleteAll();
        entries.deleteAll();
        mediaItems.deleteAll();
        users.deleteAll();

        steveUser = users.save(new AppUser("steve", passwordEncoder.encode("password1234")));
        steve = AppUserPrincipal.of(steveUser);
        anna = AppUserPrincipal.of(users.save(new AppUser("anna", passwordEncoder.encode("password5678"))));

        provider = Mockito.mock(MetadataProvider.class);
        when(providers.forType(any())).thenReturn(provider);
    }

    private MediaItem item(MediaType type, String externalId, String title) {
        MediaItem media = new MediaItem();
        media.setSource(externalId == null ? MetadataSource.MANUAL : MetadataSource.TMDB);
        media.setMediaType(type);
        media.setExternalId(externalId);
        media.setTitle(title);
        return mediaItems.save(media);
    }

    private Entry track(AppUser user, MediaType type, String externalId, String title, Integer rating) {
        Entry entry = new Entry(user, item(type, externalId, title), EntryStatus.COMPLETED);
        entry.setRating(rating);
        return entries.save(entry);
    }

    private static SearchResult result(String externalId, String title) {
        return new SearchResult(
                MetadataSource.TMDB, MediaType.MOVIE, externalId, title, 2020, "Overview.", null, null);
    }

    @Test
    void tellsAnEmptyLibraryThereIsNothingToGoOn() throws Exception {
        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seedCount").value(0))
                .andExpect(jsonPath("$.ratedCount").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        // Nothing to ask about, so the provider is never called.
        verify(provider, never()).recommendationsFor(any(), anyList());
    }

    @Test
    void separatesHavingNoScoresFromHavingOnlyHandTypedOnes() throws Exception {
        // Scored a 9, but typed in by hand: no provider id to ask about.
        track(steveUser, MediaType.MOVIE, null, "A film I typed in", 9);

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.seedCount").value(0))
                // The UI needs these apart to explain itself: one says "score
                // something", the other says "your scored titles are manual".
                .andExpect(jsonPath("$.ratedCount").value(1))
                .andExpect(jsonPath("$.items").isEmpty());

        verify(provider, never()).recommendationsFor(any(), anyList());
    }

    @Test
    void ignoresTitlesScoredTooLowToBeAnEndorsement() throws Exception {
        track(steveUser, MediaType.MOVIE, "100", "Mediocre", RecommendationService.MINIMUM_SEED_RATING - 1);

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.seedCount").value(0))
                .andExpect(jsonPath("$.ratedCount").value(1));

        verify(provider, never()).recommendationsFor(any(), anyList());
    }

    @Test
    void ranksConsensusAcrossSeedsAboveASingleHighScore() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "Loved it", 10);
        track(steveUser, MediaType.MOVIE, "2", "Also good", 8);
        track(steveUser, MediaType.MOVIE, "3", "Liked it", 7);

        when(provider.recommendationsFor(any(), anyList())).thenReturn(Map.of(
                // Only the 10 points at this one: score 10.
                "1", List.of(result("500", "Single strong signal")),
                // The 8 and the 7 both point here: score 15, so it wins.
                "2", List.of(result("600", "Agreed on")),
                "3", List.of(result("600", "Agreed on"))));

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.seedCount").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("Agreed on"))
                .andExpect(jsonPath("$.items[0].seedMatches").value(2))
                // Attribution names the best-scored seed that pointed here.
                .andExpect(jsonPath("$.items[0].becauseOfTitle").value("Also good"))
                .andExpect(jsonPath("$.items[0].becauseOfRating").value(8))
                .andExpect(jsonPath("$.items[1].title").value("Single strong signal"))
                .andExpect(jsonPath("$.items[1].seedMatches").value(1))
                .andExpect(jsonPath("$.items[1].becauseOfTitle").value("Loved it"));
    }

    @Test
    void keepsTheProvidersOwnOrderWithinASetOfEquallyScoredSuggestions() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "A seed", 10);

        // Everything here scores 10, from the one seed. The provider returned
        // them most-relevant-first and that order has to survive, or the page
        // comes out alphabetical.
        when(provider.recommendationsFor(any(), anyList())).thenReturn(Map.of(
                "1", List.of(
                        result("500", "Zeta, the strongest match"),
                        result("600", "Alpha, the weakest match"))));

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.items[0].title").value("Zeta, the strongest match"))
                .andExpect(jsonPath("$.items[1].title").value("Alpha, the weakest match"));
    }

    @Test
    void stillPutsConsensusAboveTheProvidersOrdering() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "First seed", 8);
        track(steveUser, MediaType.MOVIE, "2", "Second seed", 8);

        when(provider.recommendationsFor(any(), anyList())).thenReturn(Map.of(
                // Top of one list, but only one seed points at it.
                "1", List.of(result("500", "Top of one list"), result("600", "Agreed on")),
                "2", List.of(result("700", "Top of the other"), result("600", "Agreed on"))));

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.items[0].title").value("Agreed on"))
                .andExpect(jsonPath("$.items[0].seedMatches").value(2));
    }

    @Test
    void neverSuggestsSomethingAlreadyInTheLibrary() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "A seed", 9);
        track(steveUser, MediaType.MOVIE, "700", "Already seen this", 4);
        // Also on the watchlist, unscored: still not a suggestion.
        Entry queued = track(steveUser, MediaType.MOVIE, "800", "On the list", null);
        queued.setStatus(EntryStatus.WANT);
        entries.save(queued);

        when(provider.recommendationsFor(any(), anyList())).thenReturn(Map.of(
                "1", List.of(
                        result("700", "Already seen this"),
                        result("800", "On the list"),
                        result("900", "Genuinely new"))));

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].externalId").value("900"));
    }

    @Test
    void reusesTheCacheUntilTheSeedsChange() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "A seed", 9);
        when(provider.recommendationsFor(any(), anyList()))
                .thenReturn(Map.of("1", List.of(result("900", "Suggested"))));

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.items[0].title").value("Suggested"));

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.fromCache").value(true))
                .andExpect(jsonPath("$.items[0].title").value("Suggested"));

        verify(provider, times(1)).recommendationsFor(any(), anyList());

        // Scoring something new must not leave the user waiting out the TTL.
        track(steveUser, MediaType.MOVIE, "2", "Another seed", 10);

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.fromCache").value(false));

        verify(provider, times(2)).recommendationsFor(any(), anyList());
    }

    @Test
    void recomputesWhenAskedToRefresh() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "A seed", 9);
        when(provider.recommendationsFor(any(), anyList()))
                .thenReturn(Map.of("1", List.of(result("900", "Suggested"))));

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)));
        mockMvc.perform(get("/api/recommendations?type=movie&refresh=true").with(user(steve)))
                .andExpect(jsonPath("$.fromCache").value(false));

        verify(provider, times(2)).recommendationsFor(any(), anyList());
    }

    @Test
    void servesTheLastGoodListWhenTheProviderIsDown() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "A seed", 9);
        when(provider.recommendationsFor(any(), anyList()))
                .thenReturn(Map.of("1", List.of(result("900", "Suggested"))));
        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)));

        // A new score invalidates the cache, and now the provider fails.
        track(steveUser, MediaType.MOVIE, "2", "Another seed", 10);
        when(provider.recommendationsFor(any(), anyList()))
                .thenThrow(new ProviderException("Couldn't reach TMDB."));

        // A stale list beats an error page.
        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCache").value(true))
                .andExpect(jsonPath("$.items[0].title").value("Suggested"));
    }

    @Test
    void reportsAnUnreachableProviderWhenThereIsNoCacheToFallBackOn() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "A seed", 9);
        when(provider.recommendationsFor(any(), anyList()))
                .thenThrow(new ProviderException("Film and TV search is not configured. Set TMDB_API_KEY."));

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("provider_unavailable"))
                .andExpect(jsonPath("$.message").value(
                        "Film and TV search is not configured. Set TMDB_API_KEY."));
    }

    @Test
    void capsHowManySeedsItAsksAbout() throws Exception {
        for (int index = 0; index < RecommendationService.MAX_SEEDS + 5; index++) {
            track(steveUser, MediaType.MOVIE, String.valueOf(1000 + index), "Seed " + index, 9);
        }
        when(provider.recommendationsFor(any(), anyList())).thenReturn(Map.of());

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.seedCount").value(RecommendationService.MAX_SEEDS));
    }

    @Test
    void buildsEachUsersSuggestionsFromOnlyTheirOwnScores() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "Steve's favourite", 10);

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(anna)))
                .andExpect(jsonPath("$.seedCount").value(0))
                .andExpect(jsonPath("$.ratedCount").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        verify(provider, never()).recommendationsFor(any(), anyList());
    }

    @Test
    void keepsTheThreeMediaTypesApart() throws Exception {
        track(steveUser, MediaType.GAME, "1", "A game I loved", 10);

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(jsonPath("$.seedCount").value(0))
                .andExpect(jsonPath("$.ratedCount").value(0));

        when(provider.recommendationsFor(any(), anyList()))
                .thenReturn(Map.of("1", List.of(result("900", "Another game"))));

        mockMvc.perform(get("/api/recommendations?type=game").with(user(steve)))
                .andExpect(jsonPath("$.mediaType").value("GAME"))
                .andExpect(jsonPath("$.seedCount").value(1));
    }

    @Test
    void requiresSigningIn() throws Exception {
        mockMvc.perform(get("/api/recommendations?type=movie"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void survivesACachedPayloadItCanNoLongerRead() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "A seed", 9);
        when(provider.recommendationsFor(any(), anyList()))
                .thenReturn(Map.of("1", List.of(result("900", "Suggested"))));
        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)));

        // Simulate a payload written by an older version of the record.
        RecommendationCache cache = caches.findByUserIdAndMediaType(steve.getId(), MediaType.MOVIE).orElseThrow();
        cache.replaceWith(cache.getSeedFingerprint(), "{{{ not json at all");
        caches.saveAndFlush(cache);

        // Recomputes rather than failing every request until the row is cleared.
        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Suggested"));
    }

    @Test
    void storesOneCacheRowPerUserAndType() throws Exception {
        track(steveUser, MediaType.MOVIE, "1", "A seed", 9);
        when(provider.recommendationsFor(any(), anyList()))
                .thenReturn(Map.of("1", List.of(result("900", "Suggested"))));

        mockMvc.perform(get("/api/recommendations?type=movie").with(user(steve)));
        mockMvc.perform(get("/api/recommendations?type=movie&refresh=true").with(user(steve)));

        assertThat(caches.count()).isEqualTo(1);
    }
}
