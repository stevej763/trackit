package dev.stevejones.trackit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.stevejones.trackit.IntegrationTest;
import dev.stevejones.trackit.entry.Entry;
import dev.stevejones.trackit.entry.EntryRepository;
import dev.stevejones.trackit.entry.EntryStatus;
import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaItemRepository;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Signs in for real throughout, since what's under test is mostly what happens to sessions. */
class AccountApiTest extends IntegrationTest {

    private static final org.springframework.http.MediaType JSON = org.springframework.http.MediaType.APPLICATION_JSON;

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired EntryRepository entries;
    @Autowired MediaItemRepository mediaItems;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LoginThrottle throttle;

    private AppUser steve;
    private AppUser anna;

    @BeforeEach
    void setUp() {
        throttle.clear();
        entries.deleteAll();
        mediaItems.deleteAll();
        users.deleteAll();
        steve = users.save(new AppUser("steve", passwordEncoder.encode("password1234")));
        anna = users.save(new AppUser("anna", passwordEncoder.encode("password5678")));
    }

    private Cookie signIn(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("TRACKIT_SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private int meStatus(Cookie session) throws Exception {
        return mockMvc.perform(get("/api/auth/me").cookie(session)).andReturn().getResponse().getStatus();
    }

    private MediaItem catalogue(MetadataSource source, String externalId, String title) {
        MediaItem item = new MediaItem();
        item.setSource(source);
        item.setMediaType(MediaType.MOVIE);
        item.setExternalId(externalId);
        item.setTitle(title);
        return mediaItems.save(item);
    }

    @Test
    void changesThePasswordAndSignsOutEveryOtherSession() throws Exception {
        Cookie laptop = signIn("steve", "password1234");
        Cookie phone = signIn("steve", "password1234");
        Cookie annas = signIn("anna", "password5678");

        mockMvc.perform(put("/api/account/password")
                        .cookie(laptop)
                        .with(csrf())
                        .contentType(JSON)
                        .content("""
                                {"currentPassword":"password1234","newPassword":"a much better one"}"""))
                .andExpect(status().isNoContent());

        assertThat(meStatus(laptop)).isEqualTo(200);
        assertThat(meStatus(phone)).isEqualTo(401);
        // Nobody else's sessions are touched.
        assertThat(meStatus(annas)).isEqualTo(200);

        assertThat(passwordEncoder.matches("a much better one",
                users.findById(steve.getId()).orElseThrow().getPasswordHash())).isTrue();
        signIn("steve", "a much better one");
    }

    @Test
    void wantsTheCurrentPasswordBeforeChangingIt() throws Exception {
        Cookie session = signIn("steve", "password1234");

        mockMvc.perform(put("/api/account/password")
                        .cookie(session)
                        .with(csrf())
                        .contentType(JSON)
                        .content("""
                                {"currentPassword":"a guess at it","newPassword":"a much better one"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.currentPassword").value("That isn't your current password"));

        assertThat(passwordEncoder.matches("password1234",
                users.findById(steve.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void holdsANewPasswordToTheSameRulesAsSigningUp() throws Exception {
        Cookie session = signIn("steve", "password1234");

        mockMvc.perform(put("/api/account/password")
                        .cookie(session)
                        .with(csrf())
                        .contentType(JSON)
                        .content("{\"currentPassword\":\"password1234\",\"newPassword\":\"" + "é".repeat(40) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").exists());
    }

    @Test
    void throttlesGuessesAtTheCurrentPasswordLikeSignIns() throws Exception {
        // A stolen session shouldn't be a faster way to guess the password.
        Cookie session = signIn("steve", "password1234");
        for (int attempt = 0; attempt < LoginThrottle.FAILURES_PER_USERNAME; attempt++) {
            mockMvc.perform(put("/api/account/password")
                            .cookie(session)
                            .with(csrf())
                            .contentType(JSON)
                            .content("""
                                    {"currentPassword":"wrong guess","newPassword":"a much better one"}"""))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(put("/api/account/password")
                        .cookie(session)
                        .with(csrf())
                        .contentType(JSON)
                        .content("""
                                {"currentPassword":"password1234","newPassword":"a much better one"}"""))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void deletesTheAccountWithEverythingOnlyItUsed() throws Exception {
        MediaItem shared = catalogue(MetadataSource.TMDB, "693134", "Dune: Part Two");
        MediaItem handTyped = catalogue(MetadataSource.MANUAL, null, "A home video");
        entries.save(new Entry(steve, shared, EntryStatus.COMPLETED));
        entries.save(new Entry(steve, handTyped, EntryStatus.WANT));
        entries.save(new Entry(anna, shared, EntryStatus.WANT));
        Cookie session = signIn("steve", "password1234");
        Cookie otherDevice = signIn("steve", "password1234");

        MvcResult result = mockMvc.perform(delete("/api/account")
                        .cookie(session)
                        .with(csrf())
                        .contentType(JSON)
                        .content("""
                                {"password":"password1234"}"""))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(users.findByUsername("steve")).isEmpty();
        assertThat(mediaItems.findById(handTyped.getId())).isEmpty();
        // The shared catalogue row stays, along with anna's entry for it.
        assertThat(mediaItems.findById(shared.getId())).isPresent();
        assertThat(entries.count()).isEqualTo(1);

        assertThat(meStatus(session)).isEqualTo(401);
        assertThat(meStatus(otherDevice)).isEqualTo(401);
        // Like logging out: a fresh CSRF token, so signing up again just works.
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=") && !cookie.startsWith("XSRF-TOKEN=;"));
    }

    @Test
    void keepsTheAccountWhenThePasswordIsWrong() throws Exception {
        Cookie session = signIn("steve", "password1234");

        mockMvc.perform(delete("/api/account")
                        .cookie(session)
                        .with(csrf())
                        .contentType(JSON)
                        .content("""
                                {"password":"not it"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());

        assertThat(users.findByUsername("steve")).isPresent();
        assertThat(meStatus(session)).isEqualTo(200);
    }

    @Test
    void exportsTheWholeLibraryAsADownload() throws Exception {
        MediaItem dune = catalogue(MetadataSource.TMDB, "693134", "Dune: Part Two");
        Entry entry = new Entry(steve, dune, EntryStatus.COMPLETED);
        entry.setRating(9);
        entry.setReview("Sand, but enormous.");
        entries.save(entry);
        entries.save(new Entry(anna, catalogue(MetadataSource.MANUAL, null, "Not steve's"), EntryStatus.WANT));
        Cookie session = signIn("steve", "password1234");

        mockMvc.perform(get("/api/account/export").cookie(session))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"trackit-steve-")))
                .andExpect(jsonPath("$.format").value(1))
                .andExpect(jsonPath("$.username").value("steve"))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].rating").value(9))
                .andExpect(jsonPath("$.entries[0].review").value("Sand, but enormous."))
                .andExpect(jsonPath("$.entries[0].mediaItem.externalId").value("693134"));
    }

    @Test
    void requiresSigningIn() throws Exception {
        mockMvc.perform(get("/api/account/export")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/account").with(csrf()).contentType(JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }
}
