package dev.stevejones.trackit.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stevejones.trackit.IntegrationTest;
import dev.stevejones.trackit.auth.AppUser;
import dev.stevejones.trackit.auth.AppUserPrincipal;
import dev.stevejones.trackit.auth.AppUserRepository;
import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaItemRepository;
import dev.stevejones.trackit.media.MetadataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class LibraryApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired EntryRepository entries;
    @Autowired MediaItemRepository mediaItems;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private AppUserPrincipal steve;
    private AppUserPrincipal anna;

    @BeforeEach
    void setUp() {
        entries.deleteAll();
        mediaItems.deleteAll();
        users.deleteAll();
        steve = AppUserPrincipal.of(users.save(new AppUser("steve", passwordEncoder.encode("password1234"))));
        anna = AppUserPrincipal.of(users.save(new AppUser("anna", passwordEncoder.encode("password5678"))));
    }

    private MockHttpServletRequestBuilder as(AppUserPrincipal principal, MockHttpServletRequestBuilder builder) {
        return builder.with(user(principal)).with(csrf());
    }

    private long addManual(AppUserPrincipal principal, String type, String status, String title, Integer year)
            throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "mediaType", type,
                "status", status,
                "manual", year == null
                        ? java.util.Map.of("title", title)
                        : java.util.Map.of("title", title, "releaseYear", year)));

        String response = mockMvc.perform(as(principal, post("/api/entries"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    /** A provider title already in the shared catalogue, so adding it needs no network. */
    private void catalogueDune() {
        MediaItem dune = new MediaItem();
        dune.setSource(MetadataSource.TMDB);
        dune.setMediaType(dev.stevejones.trackit.media.MediaType.MOVIE);
        dune.setExternalId("693134");
        dune.setTitle("Dune: Part Two");
        mediaItems.save(dune);
    }

    private Instant finishedAtOf(String response) throws Exception {
        return Instant.parse(objectMapper.readTree(response).get("finishedAt").asText());
    }

    private List<String> titlesFrom(String query) throws Exception {
        String response = mockMvc.perform(as(steve, get("/api/entries" + query)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode items = objectMapper.readTree(response).get("items");
        return items.findValuesAsText("title");
    }

    @Test
    void addsAManualTitleAndReadsItBack() throws Exception {
        long id = addManual(steve, "MOVIE", "WANT", "Arrival", 2016);

        mockMvc.perform(as(steve, get("/api/entries/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WANT"))
                .andExpect(jsonPath("$.rating").value((Object) null))
                .andExpect(jsonPath("$.mediaItem.title").value("Arrival"))
                .andExpect(jsonPath("$.mediaItem.source").value("MANUAL"))
                .andExpect(jsonPath("$.mediaItem.releaseYear").value(2016));
    }

    @Test
    void recordsAScoreAndAReview() throws Exception {
        long id = addManual(steve, "MOVIE", "WANT", "Arrival", 2016);

        mockMvc.perform(as(steve, put("/api/entries/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED","rating":9,"review":"Holds up.",
                                 "startedOn":"2026-09-01","finishedAt":"2026-09-02T21:30:00+01:00"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(9))
                .andExpect(jsonPath("$.review").value("Holds up."))
                // An instant: the offset it was sent with is normalised to UTC.
                .andExpect(jsonPath("$.finishedAt").value("2026-09-02T20:30:00Z"));

        // Null clears, which is how the detail form removes a score.
        mockMvc.perform(as(steve, put("/api/entries/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED","rating":null,"review":null,
                                 "startedOn":null,"finishedAt":null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value((Object) null))
                .andExpect(jsonPath("$.review").value((Object) null));
    }

    @Test
    void rejectsAScoreOutsideOneToTen() throws Exception {
        long id = addManual(steve, "MOVIE", "WANT", "Arrival", 2016);

        mockMvc.perform(as(steve, put("/api/entries/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED","rating":11}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.rating").exists());
    }

    @Test
    void recordsTheMomentWhenSomethingIsFinishedFromTheGrid() throws Exception {
        long id = addManual(steve, "GAME", "IN_PROGRESS", "Hades", 2020);
        Instant before = Instant.now().minusSeconds(1);

        String response = mockMvc.perform(as(steve, patch("/api/entries/" + id + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // A moment, not the server's idea of today's date: which day that is
        // depends on where the user is.
        assertThat(finishedAtOf(response)).isBetween(before, Instant.now());
    }

    @Test
    void recordsTheMomentWhenFinishedWithoutADateButKeepsALaterClearing() throws Exception {
        long id = addManual(steve, "MOVIE", "WANT", "Arrival", 2016);
        Instant before = Instant.now().minusSeconds(1);

        // Flipping to finished without touching the date field: now is meant,
        // and the stats page counts completions by month, so it needs one.
        String response = mockMvc.perform(as(steve, put("/api/entries/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED","rating":8}"""))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(finishedAtOf(response)).isBetween(before, Instant.now());

        // Clearing it afterwards is an explicit choice and must stick.
        mockMvc.perform(as(steve, put("/api/entries/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED","rating":8,"finishedAt":null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedAt").value((Object) null));
    }

    @Test
    void filtersByTypeStatusScoreAndTitle() throws Exception {
        long film = addManual(steve, "MOVIE", "COMPLETED", "Dune: Part Two", 2024);
        addManual(steve, "TV", "IN_PROGRESS", "Severance", 2022);
        addManual(steve, "GAME", "WANT", "Elden Ring", 2022);

        mockMvc.perform(as(steve, put("/api/entries/" + film))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED","rating":9}"""))
                .andExpect(status().isOk());

        assertThat(titlesFrom("")).hasSize(3);
        assertThat(titlesFrom("?type=tv")).containsExactly("Severance");
        // Lower case comes from the SPA's URLs.
        assertThat(titlesFrom("?status=in_progress")).containsExactly("Severance");
        assertThat(titlesFrom("?minRating=9")).containsExactly("Dune: Part Two");
        assertThat(titlesFrom("?minRating=10")).isEmpty();
        assertThat(titlesFrom("?q=eld")).containsExactly("Elden Ring");
        assertThat(titlesFrom("?q=SEVER")).containsExactly("Severance");
        assertThat(titlesFrom("?type=movie&status=completed&minRating=5"))
                .containsExactly("Dune: Part Two");
    }

    @Test
    void sortsUnscoredAndUnfinishedEntriesLastInEitherDirection() throws Exception {
        long scored = addManual(steve, "MOVIE", "COMPLETED", "Scored", 2024);
        addManual(steve, "TV", "WANT", "Unscored one", 2022);
        addManual(steve, "GAME", "WANT", "Unscored two", 2020);

        // finishedAt is given explicitly: omitting it means "clear it", which is
        // the contract this very test would otherwise be relying on by accident.
        mockMvc.perform(as(steve, put("/api/entries/" + scored))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED","rating":7,"finishedAt":"2026-09-30T20:00:00Z"}"""))
                .andExpect(status().isOk());

        // Postgres would put NULLs first on a descending sort; the scored entry
        // must lead whichever way the user flips it.
        assertThat(titlesFrom("?sort=rating&direction=desc").get(0)).isEqualTo("Scored");
        assertThat(titlesFrom("?sort=rating&direction=asc").get(0)).isEqualTo("Scored");
        assertThat(titlesFrom("?sort=finished&direction=desc").get(0)).isEqualTo("Scored");
    }

    @Test
    void sortsByTitleAndYear() throws Exception {
        addManual(steve, "MOVIE", "WANT", "Zodiac", 2007);
        addManual(steve, "MOVIE", "WANT", "Arrival", 2016);
        addManual(steve, "MOVIE", "WANT", "Memento", 2000);

        assertThat(titlesFrom("?sort=title&direction=asc"))
                .containsExactly("Arrival", "Memento", "Zodiac");
        assertThat(titlesFrom("?sort=year&direction=desc"))
                .containsExactly("Arrival", "Zodiac", "Memento");
    }

    @Test
    void rejectsAnUnknownSortRatherThanIgnoringIt() throws Exception {
        mockMvc.perform(as(steve, get("/api/entries?sort=;drop")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void pagesWithAStableOrder() throws Exception {
        for (int index = 0; index < 5; index++) {
            addManual(steve, "MOVIE", "WANT", "Film " + index, 2000 + index);
        }

        mockMvc.perform(as(steve, get("/api/entries?size=2&page=0")))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(as(steve, get("/api/entries?size=2&page=2")))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void refusesToTrackTheSameTitleTwice() throws Exception {
        String body = """
                {"mediaType":"MOVIE","status":"WANT","source":"TMDB","externalId":"693134"}""";

        // No TMDB key is configured in tests, so this cannot reach the network.
        mockMvc.perform(as(steve, post("/api/entries"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("provider_unavailable"));
    }

    @Test
    void answersADoubleSubmittedAddWithAConflictNotAServerError() throws Exception {
        catalogueDune();

        String body = """
                {"mediaType":"MOVIE","status":"WANT","source":"TMDB","externalId":"693134"}""";
        int attempts = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Future<Integer>> statuses = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(as(steve, post("/api/entries"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            start.countDown();

            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                codes.add(status.get(30, TimeUnit.SECONDS));
            }
            // Whichever way each request lost (the existence check or the unique
            // constraint), it is a 409, never a 500.
            assertThat(codes).containsOnlyOnce(201).containsOnly(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(entries.count()).isEqualTo(1);
    }

    @Test
    void needsEitherAProviderReferenceOrManualDetails() throws Exception {
        mockMvc.perform(as(steve, post("/api/entries"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaType":"MOVIE","status":"WANT"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void keepsEachUsersLibraryEntirelySeparate() throws Exception {
        long steveEntry = addManual(steve, "MOVIE", "COMPLETED", "Steve's film", 2024);

        // Anna sees nothing of Steve's.
        mockMvc.perform(as(anna, get("/api/entries")))
                .andExpect(jsonPath("$.totalItems").value(0));

        // A 404, not a 403: ids aren't confirmed to exist for other users.
        mockMvc.perform(as(anna, get("/api/entries/" + steveEntry)))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(anna, put("/api/entries/" + steveEntry))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DROPPED","rating":1}"""))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(anna, patch("/api/entries/" + steveEntry + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DROPPED"}"""))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(anna, delete("/api/entries/" + steveEntry)))
                .andExpect(status().isNotFound());

        // Steve's entry is untouched by all of that.
        mockMvc.perform(as(steve, get("/api/entries/" + steveEntry)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void removesAnEntryButLeavesTheSharedCatalogueAlone() throws Exception {
        catalogueDune();
        String response = mockMvc.perform(as(steve, post("/api/entries"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaType":"MOVIE","status":"WANT","source":"TMDB","externalId":"693134"}"""))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(as(steve, delete("/api/entries/" + id))).andExpect(status().isNoContent());
        mockMvc.perform(as(steve, get("/api/entries/" + id))).andExpect(status().isNotFound());

        assertThat(entries.count()).isZero();
        assertThat(mediaItems.count()).isEqualTo(1);
    }

    @Test
    void removesAHandTypedTitleAlongWithItsEntry() throws Exception {
        // Nothing else can point at a manual catalogue row, so it would be an orphan.
        long id = addManual(steve, "MOVIE", "WANT", "Arrival", 2016);

        mockMvc.perform(as(steve, delete("/api/entries/" + id))).andExpect(status().isNoContent());

        assertThat(entries.count()).isZero();
        assertThat(mediaItems.count()).isZero();
    }

    @Test
    void correctsAHandTypedTitleWithoutLosingTheVerdict() throws Exception {
        long id = addManual(steve, "MOVIE", "COMPLETED", "Arival", 2061);
        mockMvc.perform(as(steve, put("/api/entries/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED","rating":9,"review":"Heptapods."}"""))
                .andExpect(status().isOk());

        mockMvc.perform(as(steve, put("/api/entries/" + id + "/details"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" Arrival ","releaseYear":2016,"posterUrl":"https://example.com/arrival.jpg"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaItem.title").value("Arrival"))
                .andExpect(jsonPath("$.mediaItem.releaseYear").value(2016))
                .andExpect(jsonPath("$.mediaItem.posterUrl").value("https://example.com/arrival.jpg"))
                .andExpect(jsonPath("$.rating").value(9))
                .andExpect(jsonPath("$.review").value("Heptapods."));
    }

    @Test
    void leavesAProviderTitlesDetailsToTheProvider() throws Exception {
        // Shared by everyone tracking it, so one user mustn't rewrite it.
        catalogueDune();
        String response = mockMvc.perform(as(steve, post("/api/entries"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaType":"MOVIE","status":"WANT","source":"TMDB","externalId":"693134"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(as(steve, put("/api/entries/" + id + "/details"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Something else"}"""))
                .andExpect(status().isBadRequest());

        assertThat(mediaItems.findAll()).extracting(MediaItem::getTitle).containsExactly("Dune: Part Two");
    }

    @Test
    void acceptsOnlyWebAddressesForArtwork() throws Exception {
        for (String url : List.of("javascript:alert(1)", "data:image/png;base64,AAAA", "ftp://example.com/a.jpg")) {
            mockMvc.perform(as(steve, post("/api/entries"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "mediaType", "MOVIE",
                                    "status", "WANT",
                                    "manual", java.util.Map.of("title", "Arrival", "posterUrl", url)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['manual.posterUrl']").exists());
        }
        assertThat(entries.count()).isZero();

        // Blank still means "no artwork", and either scheme in any case is fine.
        for (String url : List.of("", "HTTPS://example.com/a.jpg", "http://example.com/a.jpg")) {
            mockMvc.perform(as(steve, post("/api/entries"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "mediaType", "MOVIE",
                                    "status", "WANT",
                                    "manual", java.util.Map.of("title", "Arrival " + url, "posterUrl", url)))))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void hasNowhereToRefreshAHandTypedTitleFrom() throws Exception {
        long id = addManual(steve, "MOVIE", "WANT", "Arrival", 2016);

        mockMvc.perform(as(steve, post("/api/entries/" + id + "/refresh")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explainsThatRefreshingNeedsAProviderKey() throws Exception {
        // The test context has no TMDB key: the refusal must happen before any
        // request is made, and say what to do.
        catalogueDune();
        String response = mockMvc.perform(as(steve, post("/api/entries"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaType":"MOVIE","status":"WANT","source":"TMDB","externalId":"693134"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(as(steve, post("/api/entries/" + id + "/refresh")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("TMDB_API_KEY")));
    }

    @Test
    void refusesToEditOrRefreshSomeoneElsesEntry() throws Exception {
        long annas = addManual(anna, "MOVIE", "WANT", "Arrival", 2016);

        mockMvc.perform(as(steve, put("/api/entries/" + annas + "/details"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Mine now"}"""))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(steve, post("/api/entries/" + annas + "/refresh")))
                .andExpect(status().isNotFound());
    }
}
