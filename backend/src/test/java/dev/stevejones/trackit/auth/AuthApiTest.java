package dev.stevejones.trackit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.stevejones.trackit.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class AuthApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LoginThrottle throttle;

    @BeforeEach
    void clean() {
        // One application context is shared across test classes, and with it
        // the throttle's counts.
        throttle.clear();
        users.deleteAll();
    }

    private org.springframework.test.web.servlet.ResultActions signIn(String username, String password)
            throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void registersAnAccountAndStoresOnlyAHash() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"steve","password":"correct horse battery"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("steve"))
                // The response must never echo the password back.
                .andExpect(jsonPath("$.password").doesNotExist());

        AppUser saved = users.findByUsername("steve").orElseThrow();
        assertThat(saved.getPasswordHash())
                .isNotEqualTo("correct horse battery")
                .startsWith("$2");
        assertThat(passwordEncoder.matches("correct horse battery", saved.getPasswordHash())).isTrue();
    }

    @Test
    void treatsUsernamesAsCaseInsensitiveWhenRejectingDuplicates() throws Exception {
        users.save(new AppUser("steve", passwordEncoder.encode("whatever123")));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"STEVE","password":"something else"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"));

        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void returnsPerFieldMessagesForAWeakRegistration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.details.username").exists())
                .andExpect(jsonPath("$.details.password").exists());

        assertThat(users.count()).isZero();
    }

    @Test
    void rejectsAPasswordLongerThanBcryptCanHashInsteadOfFailing() throws Exception {
        // 40 characters but 80 bytes: BCrypt's limit is in bytes, and the
        // encoder throws past it, which used to surface as a 500.
        String accented = "é".repeat(40);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"steve\",\"password\":\"" + accented + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.details.password").value(
                        org.hamcrest.Matchers.containsString("too long")));

        assertThat(users.count()).isZero();

        // Exactly 72 bytes is fine.
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"steve\",\"password\":\"" + "é".repeat(36) + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsUsernamesWithCharactersThatWouldBeAmbiguous() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"steve jones!","password":"longenoughpassword"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.username").exists());
    }

    @Test
    void givesTheSameAnswerForAWrongPasswordAndAnUnknownUser() throws Exception {
        users.save(new AppUser("steve", passwordEncoder.encode("therealpassword")));

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"steve","password":"notthepassword"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownUser = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody","password":"notthepassword"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Identical bodies, so the API can't be used to discover who has an account.
        assertThat(wrongPassword).isEqualTo(unknownUser);
    }

    @Test
    void slowsDownRepeatedGuessesAtOnePassword() throws Exception {
        users.save(new AppUser("steve", passwordEncoder.encode("therealpassword")));
        users.save(new AppUser("anna", passwordEncoder.encode("annaspassword")));
        for (int attempt = 0; attempt < LoginThrottle.FAILURES_PER_USERNAME; attempt++) {
            signIn("steve", "guess" + attempt).andExpect(status().isUnauthorized());
        }

        // Refused before the password is even checked, so a right guess now
        // looks the same as a wrong one.
        signIn("Steve", "therealpassword")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("too_many_requests"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Retry-After"));

        // Other accounts on the same address are unaffected.
        signIn("anna", "annaspassword").andExpect(status().isOk());
    }

    @Test
    void treatsAnOverlongPasswordAtSignInAsJustAWrongOne() throws Exception {
        // Longer than BCrypt can hash; no account could have it, so it's a 401
        // like any other wrong password, not a server error.
        users.save(new AppUser("steve", passwordEncoder.encode("therealpassword")));

        signIn("steve", "é".repeat(40)).andExpect(status().isUnauthorized());
    }

    @Test
    void limitsSignUpsFromOneAddress() throws Exception {
        for (int i = 0; i < LoginThrottle.SIGNUPS_PER_ADDRESS; i++) {
            mockMvc.perform(post("/api/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"user" + i + "\",\"password\":\"longenoughpassword\"}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"onemore","password":"longenoughpassword"}"""))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void offersSignUpByDefault() throws Exception {
        mockMvc.perform(get("/api/auth/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupAllowed").value(true));
    }

    @Test
    void answersAnonymousApiCallsWithAJsonError() throws Exception {
        mockMvc.perform(get("/api/entries"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"))
                .andExpect(jsonPath("$.message").value("Please sign in"));
    }

    @Test
    void refusesAWriteWithNoCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"steve","password":"longenoughpassword"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("csrf_failed"));

        assertThat(users.count()).isZero();
    }

    @Test
    void leavesTheHealthEndpointOpenForTheContainerHealthcheck() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
