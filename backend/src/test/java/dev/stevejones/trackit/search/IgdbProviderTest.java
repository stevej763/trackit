package dev.stevejones.trackit.search;

import static dev.stevejones.trackit.search.TmdbProviderTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class IgdbProviderTest {

    private static final IgdbProperties PROPERTIES = new IgdbProperties(
            "client-id",
            "client-secret",
            "https://api.igdb.com/v4",
            "https://id.twitch.tv/oauth2/token",
            "https://images.igdb.com/igdb/image/upload/t_cover_big",
            "https://images.igdb.com/igdb/image/upload/t_screenshot_big");

    private MockRestServiceServer server;
    private IgdbProvider provider;

    @BeforeEach
    void setUp() {
        // One builder, so the token call and the games call share a mock server.
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        provider = new IgdbProvider(PROPERTIES, new IgdbTokenStore(PROPERTIES, builder), builder);
    }

    private void expectTokenCall() throws IOException {
        server.expect(ExpectedCount.once(),
                        requestTo(Matchers.startsWith("https://id.twitch.tv/oauth2/token")))
                .andExpect(method(HttpMethod.POST))
                // Credentials go in the form body, never the URL, which can end up in a log.
                .andExpect(requestTo("https://id.twitch.tv/oauth2/token"))
                .andExpect(content().formDataContains(Map.of(
                        "client_id", "client-id",
                        "client_secret", "client-secret",
                        "grant_type", "client_credentials")))
                .andRespond(withSuccess(fixture("igdb-token.json"), org.springframework.http.MediaType.APPLICATION_JSON));
    }

    @Test
    void authenticatesWithTwitchThenSearchesGames() throws IOException {
        expectTokenCall();
        server.expect(ExpectedCount.once(), requestTo("https://api.igdb.com/v4/games"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Client-ID", "client-id"))
                .andExpect(header("Authorization", "Bearer twitch-token-one"))
                .andExpect(content().string(Matchers.containsString("search \"elden\"")))
                .andRespond(withSuccess(fixture("igdb-search.json"), org.springframework.http.MediaType.APPLICATION_JSON));

        List<SearchResult> results = provider.search(MediaType.GAME, "elden");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).source()).isEqualTo(MetadataSource.IGDB);
        assertThat(results.get(0).externalId()).isEqualTo("119133");
        assertThat(results.get(0).title()).isEqualTo("Elden Ring");
        // first_release_date is unix seconds, not a date string.
        assertThat(results.get(0).releaseYear()).isEqualTo(2022);
        assertThat(results.get(0).posterUrl())
                .isEqualTo("https://images.igdb.com/igdb/image/upload/t_cover_big/co4jni.jpg");

        server.verify();
    }

    @Test
    void readsDetailIncludingPlatformsAndAScreenshotBackdrop() throws IOException {
        expectTokenCall();
        server.expect(ExpectedCount.once(), requestTo("https://api.igdb.com/v4/games"))
                .andExpect(content().string(Matchers.containsString("where id = 119133")))
                .andRespond(withSuccess(fixture("igdb-detail.json"), org.springframework.http.MediaType.APPLICATION_JSON));

        MediaItem item = provider.fetchDetails(MediaType.GAME, "119133");

        assertThat(item.getTitle()).isEqualTo("Elden Ring");
        assertThat(item.getPlatforms()).containsExactly("PC (Microsoft Windows)", "PlayStation 5");
        assertThat(item.getGenres()).containsExactly("Role-playing (RPG)", "Adventure");
        assertThat(item.getBackdropUrl())
                .isEqualTo("https://images.igdb.com/igdb/image/upload/t_screenshot_big/sc8abc.jpg");
        assertThat(item.getRuntimeMinutes()).isNull();

        server.verify();
    }

    @Test
    void fetchesAFreshTokenAndRetriesOnceWhenIgdbRejectsTheOldOne() throws IOException {
        // Two token calls: the initial one, then the refresh after the 401.
        server.expect(ExpectedCount.twice(),
                        requestTo(Matchers.startsWith("https://id.twitch.tv/oauth2/token")))
                .andRespond(withSuccess(fixture("igdb-token.json"), org.springframework.http.MediaType.APPLICATION_JSON));

        server.expect(ExpectedCount.once(), requestTo("https://api.igdb.com/v4/games"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(ExpectedCount.once(), requestTo("https://api.igdb.com/v4/games"))
                .andRespond(withSuccess(fixture("igdb-search.json"), org.springframework.http.MediaType.APPLICATION_JSON));

        List<SearchResult> results = provider.search(MediaType.GAME, "elden");

        assertThat(results).hasSize(2);
        server.verify();
    }

    @Test
    void givesUpAfterOneRetryRatherThanLoopingOnA401() throws IOException {
        server.expect(ExpectedCount.manyTimes(),
                        requestTo(Matchers.startsWith("https://id.twitch.tv/oauth2/token")))
                .andRespond(withSuccess(fixture("igdb-token.json"), org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.twice(), requestTo("https://api.igdb.com/v4/games"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.search(MediaType.GAME, "elden"))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.Unauthorized.class);

        server.verify();
    }

    @Test
    void reportsBadTwitchCredentialsAsSomethingTheUserCanFix() {
        server.expect(requestTo(Matchers.startsWith("https://id.twitch.tv/oauth2/token")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> provider.search(MediaType.GAME, "elden"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("IGDB_CLIENT_ID");
    }

    @Test
    void refusesNonNumericIdsRatherThanPuttingThemInTheQuery() {
        assertThatThrownBy(() -> provider.fetchDetails(MediaType.GAME, "1; drop everything"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("IGDB game id");

        // Nothing was sent, so the injection attempt never reached IGDB.
        server.verify();
    }

    @Test
    void stripsQuotesAndSemicolonsOutOfTheSearchTerm() throws IOException {
        expectTokenCall();
        server.expect(ExpectedCount.once(), requestTo("https://api.igdb.com/v4/games"))
                .andExpect(content().string(Matchers.containsString("search \"zelda fields name\"")))
                .andRespond(withSuccess("[]", org.springframework.http.MediaType.APPLICATION_JSON));

        provider.search(MediaType.GAME, "zelda\"; fields name");

        server.verify();
    }

    @Test
    void refusesToCallIgdbAtAllWithNoCredentials() {
        IgdbProperties unconfigured = new IgdbProperties(
                "", "", PROPERTIES.baseUrl(), PROPERTIES.tokenUrl(),
                PROPERTIES.coverBaseUrl(), PROPERTIES.screenshotBaseUrl());
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer strict = MockRestServiceServer.bindTo(builder).build();
        IgdbProvider unconfiguredProvider =
                new IgdbProvider(unconfigured, new IgdbTokenStore(unconfigured, builder), builder);

        assertThatThrownBy(() -> unconfiguredProvider.search(MediaType.GAME, "elden"))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("not configured");

        strict.verify();
    }

    @Test
    void asksForEverySeedInOneRequestAndKeepsThemApart() throws IOException {
        expectTokenCall();
        server.expect(ExpectedCount.once(), requestTo("https://api.igdb.com/v4/games"))
                .andExpect(method(HttpMethod.POST))
                // One request covering both seeds, which is why the interface
                // takes a batch: TMDB cannot do this.
                .andExpect(content().string(Matchers.containsString("where id = (119133,1942)")))
                .andExpect(content().string(Matchers.containsString("similar_games.cover.image_id")))
                .andRespond(withSuccess(fixture("igdb-similar.json"),
                        org.springframework.http.MediaType.APPLICATION_JSON));

        Map<String, List<SearchResult>> bySeed =
                provider.recommendationsFor(MediaType.GAME, List.of("119133", "1942"));

        // The third fixture row has no similar_games and is left out entirely.
        assertThat(bySeed).containsOnlyKeys("119133", "1942");
        assertThat(bySeed.get("119133")).hasSize(2);
        assertThat(bySeed.get("1942")).hasSize(2);

        SearchResult darkSouls = bySeed.get("119133").get(0);
        assertThat(darkSouls.externalId()).isEqualTo("2155");
        assertThat(darkSouls.title()).isEqualTo("Dark Souls");
        assertThat(darkSouls.releaseYear()).isEqualTo(2011);
        assertThat(darkSouls.posterUrl())
                .isEqualTo("https://images.igdb.com/igdb/image/upload/t_cover_big/co2uro.jpg");
        assertThat(darkSouls.mediaType()).isEqualTo(MediaType.GAME);

        // A game with no cover keeps a null poster rather than a broken URL.
        assertThat(bySeed.get("1942").get(1).title()).isEqualTo("Control");
        assertThat(bySeed.get("1942").get(1).posterUrl()).isNull();

        server.verify();
    }

    @Test
    void makesNoRequestWhenEverySeedIdIsUnusable() {
        assertThat(provider.recommendationsFor(MediaType.GAME, List.of("not-an-id", "1; drop"))).isEmpty();
        server.verify();
    }

    @Test
    void supportsOnlyGames() {
        assertThat(provider.supports(MediaType.GAME)).isTrue();
        assertThat(provider.supports(MediaType.MOVIE)).isFalse();
    }
}
