package dev.stevejones.trackit.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

class TmdbProviderTest {

    private static final TmdbProperties PROPERTIES = new TmdbProperties(
            "test-key",
            "https://api.themoviedb.org/3",
            "https://image.tmdb.org/t/p/w500",
            "https://image.tmdb.org/t/p/w1280");

    private MockRestServiceServer server;
    private TmdbProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new TmdbProvider(PROPERTIES, builder);
    }

    static String fixture(String name) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource("fixtures/" + name).getInputStream(), StandardCharsets.UTF_8);
    }

    @Test
    void searchesMoviesAndNormalisesEachHit() throws IOException {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.themoviedb.org/3/search/movie")))
                .andExpect(queryParam("query", "dune"))
                .andExpect(queryParam("api_key", "test-key"))
                .andExpect(queryParam("include_adult", "false"))
                .andRespond(withSuccess(fixture("tmdb-search-movie.json"), org.springframework.http.MediaType.APPLICATION_JSON));

        List<SearchResult> results = provider.search(MediaType.MOVIE, "dune");

        assertThat(results).hasSize(3);
        assertThat(results.get(0).source()).isEqualTo(MetadataSource.TMDB);
        assertThat(results.get(0).mediaType()).isEqualTo(MediaType.MOVIE);
        assertThat(results.get(0).externalId()).isEqualTo("693134");
        assertThat(results.get(0).title()).isEqualTo("Dune: Part Two");
        assertThat(results.get(0).releaseYear()).isEqualTo(2024);
        assertThat(results.get(0).posterUrl())
                .isEqualTo("https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg");
        assertThat(results.get(0).trackedEntryId()).isNull();

        // An empty release_date and a null poster must not blow up or invent values.
        assertThat(results.get(2).releaseYear()).isNull();
        assertThat(results.get(2).posterUrl()).isNull();
        assertThat(results.get(2).overview()).isNull();

        server.verify();
    }

    @Test
    void readsMovieDetailIncludingRuntimeAndGenres() throws IOException {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.themoviedb.org/3/movie/693134")))
                .andRespond(withSuccess(fixture("tmdb-movie-detail.json"), org.springframework.http.MediaType.APPLICATION_JSON));

        MediaItem item = provider.fetchDetails(MediaType.MOVIE, "693134");

        assertThat(item.getSource()).isEqualTo(MetadataSource.TMDB);
        assertThat(item.getMediaType()).isEqualTo(MediaType.MOVIE);
        assertThat(item.getExternalId()).isEqualTo("693134");
        assertThat(item.getTitle()).isEqualTo("Dune: Part Two");
        assertThat(item.getReleaseYear()).isEqualTo(2024);
        assertThat(item.getRuntimeMinutes()).isEqualTo(167);
        assertThat(item.getGenres()).containsExactly("Science Fiction", "Adventure");
        assertThat(item.getBackdropUrl())
                .isEqualTo("https://image.tmdb.org/t/p/w1280/xOMo8BRK7PfcMv9HRB0FlJ3s2Ke.jpg");
        assertThat(item.getMetadataFetchedAt()).isNotNull();
        // Runtime is a film field; TV counts must stay empty.
        assertThat(item.getSeasonCount()).isNull();

        server.verify();
    }

    @Test
    void readsTvDetailFromTheShowFieldNames() throws IOException {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.themoviedb.org/3/tv/95396")))
                .andRespond(withSuccess(fixture("tmdb-tv-detail.json"), org.springframework.http.MediaType.APPLICATION_JSON));

        MediaItem item = provider.fetchDetails(MediaType.TV, "95396");

        // TMDB calls it "name" and "first_air_date" for shows, not "title"/"release_date".
        assertThat(item.getTitle()).isEqualTo("Severance");
        assertThat(item.getReleaseYear()).isEqualTo(2022);
        assertThat(item.getSeasonCount()).isEqualTo(2);
        assertThat(item.getEpisodeCount()).isEqualTo(19);
        assertThat(item.getRuntimeMinutes()).isNull();

        server.verify();
    }

    @Test
    void reportsARejectedKeyAsSomethingTheUserCanFix() {
        server.expect(MockRestRequestMatchers.anything())
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.search(MediaType.MOVIE, "dune"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("TMDB_API_KEY");
    }

    @Test
    void reportsRateLimitingSeparatelyFromAMissingTitle() {
        server.expect(MockRestRequestMatchers.anything())
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.search(MediaType.MOVIE, "dune"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("rate-limiting");
    }

    @Test
    void reportsAnUnknownTitleAsNotFound() {
        server.expect(MockRestRequestMatchers.anything()).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> provider.fetchDetails(MediaType.MOVIE, "1"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("doesn't have that title");
    }

    @Test
    void refusesToCallTmdbAtAllWithNoApiKey() {
        TmdbProperties unconfigured = new TmdbProperties(
                "  ", PROPERTIES.baseUrl(), PROPERTIES.posterBaseUrl(), PROPERTIES.backdropBaseUrl());
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer strict = MockRestServiceServer.bindTo(builder).build();
        TmdbProvider unconfiguredProvider = new TmdbProvider(unconfigured, builder);

        assertThatThrownBy(() -> unconfiguredProvider.search(MediaType.MOVIE, "dune"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("not configured");

        // No request was attempted, so manual entry stays the only path.
        strict.verify();
    }

    @Test
    void supportsOnlyFilmAndTv() {
        assertThat(provider.supports(MediaType.MOVIE)).isTrue();
        assertThat(provider.supports(MediaType.TV)).isTrue();
        assertThat(provider.supports(MediaType.GAME)).isFalse();
    }
}
