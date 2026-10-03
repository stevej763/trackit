package dev.stevejones.trackit.stats;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stevejones.trackit.IntegrationTest;
import dev.stevejones.trackit.auth.AppUser;
import dev.stevejones.trackit.auth.AppUserPrincipal;
import dev.stevejones.trackit.auth.AppUserRepository;
import dev.stevejones.trackit.entry.EntryRepository;
import dev.stevejones.trackit.media.MediaItemRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class StatsApiTest extends IntegrationTest {

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

    private long add(AppUserPrincipal principal, String type, String status, String title) throws Exception {
        String response = mockMvc.perform(post("/api/entries")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "mediaType", type,
                                "status", status,
                                "manual", java.util.Map.of("title", title)))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void score(AppUserPrincipal principal, long id, int rating, String finishedOn) throws Exception {
        mockMvc.perform(put("/api/entries/" + id)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED","rating":%d,"finishedOn":"%s"}"""
                                .formatted(rating, finishedOn)))
                .andExpect(status().isOk());
    }

    @Test
    void returnsGapFreeBucketsForAnEmptyLibrary() throws Exception {
        mockMvc.perform(get("/api/stats").with(user(steve)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.ratedItems").value(0))
                .andExpect(jsonPath("$.averageRating").value((Object) null))
                // Every bucket is present even with nothing in it, so the charts
                // never have to invent missing points.
                .andExpect(jsonPath("$.byMediaType.length()").value(3))
                .andExpect(jsonPath("$.byStatus.length()").value(5))
                .andExpect(jsonPath("$.ratingHistogram.length()").value(10))
                .andExpect(jsonPath("$.finishedByMonth.length()").value(12));
    }

    @Test
    void countsByTypeStatusAndScore() throws Exception {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        long film = add(steve, "MOVIE", "WANT", "A film");
        long show = add(steve, "TV", "IN_PROGRESS", "A show");
        add(steve, "GAME", "WANT", "A game");

        score(steve, film, 9, today);
        score(steve, show, 7, today);

        mockMvc.perform(get("/api/stats").with(user(steve)))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.ratedItems").value(2))
                .andExpect(jsonPath("$.averageRating").value(8.0))
                .andExpect(jsonPath("$.byMediaType[?(@.key=='MOVIE')].count").value(1))
                .andExpect(jsonPath("$.byMediaType[?(@.key=='TV')].count").value(1))
                .andExpect(jsonPath("$.byMediaType[?(@.key=='GAME')].count").value(1))
                .andExpect(jsonPath("$.byStatus[?(@.key=='COMPLETED')].count").value(2))
                .andExpect(jsonPath("$.byStatus[?(@.key=='WANT')].count").value(1))
                .andExpect(jsonPath("$.byStatus[?(@.key=='DROPPED')].count").value(0))
                .andExpect(jsonPath("$.ratingHistogram[?(@.key=='9')].count").value(1))
                .andExpect(jsonPath("$.ratingHistogram[?(@.key=='7')].count").value(1))
                .andExpect(jsonPath("$.ratingHistogram[?(@.key=='1')].count").value(0))
                // The current month is always the last of the twelve.
                .andExpect(jsonPath("$.finishedByMonth[11].count").value(2))
                .andExpect(jsonPath("$.averageRatingByMediaType[?(@.key=='MOVIE')].average").value(9.0));
    }

    @Test
    void countsOnlyTheRequestingUsersLibrary() throws Exception {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        long annaEntry = add(anna, "MOVIE", "WANT", "Anna's film");
        score(anna, annaEntry, 10, today);
        add(steve, "GAME", "WANT", "Steve's game");

        mockMvc.perform(get("/api/stats").with(user(steve)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.ratedItems").value(0))
                .andExpect(jsonPath("$.averageRating").value((Object) null));

        mockMvc.perform(get("/api/stats").with(user(anna)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.ratedItems").value(1))
                .andExpect(jsonPath("$.averageRating").value(10.0));
    }
}
