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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "trackit.auth.allow-signup=false")
class SignupClosedApiTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LoginThrottle throttle;

    @BeforeEach
    void clean() {
        throttle.clear();
        users.deleteAll();
    }

    @Test
    void refusesNewAccounts() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"stranger","password":"longenoughpassword"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("signup_closed"));

        assertThat(users.count()).isZero();
    }

    @Test
    void stillSignsInExistingAccounts() throws Exception {
        users.save(new AppUser("steve", passwordEncoder.encode("password1234")));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"steve","password":"password1234"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void tellsTheSignInPageNotToOfferSignUp() throws Exception {
        mockMvc.perform(get("/api/auth/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupAllowed").value(false));
    }
}
