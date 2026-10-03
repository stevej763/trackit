package dev.stevejones.trackit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stevejones.trackit.auth.AppUserDetailsService;
import dev.stevejones.trackit.common.ApiError;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Session-cookie auth for a same-origin SPA. nginx serves the app and proxies
 * /api to this service, so there is no CORS configuration to get wrong and the
 * session cookie is first-party.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Only switch this on behind HTTPS &mdash; a Secure cookie is silently
     * dropped over plain HTTP, which is how this gets run on a LAN.
     */
    private final boolean secureCookies;

    public SecurityConfig(@Value("${TRACKIT_SECURE_COOKIE:false}") boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository) throws Exception {

        http
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessHandler((request, response, authentication) -> {
                            // Logging out clears the CSRF cookie too, so issue a
                            // fresh token on the way out: otherwise the very next
                            // POST (signing back in) would be rejected.
                            csrfTokenRepository.loadDeferredToken(request, response).get().getToken();
                            response.setStatus(HttpStatus.NO_CONTENT.value());
                        }))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(objectMapper, response, HttpStatus.UNAUTHORIZED,
                                        ApiError.of("unauthenticated", "Please sign in")))
                        // Rejections raised in the filter chain never reach
                        // GlobalExceptionHandler, so give them the same ApiError
                        // shape here rather than Spring's default error body.
                        .accessDeniedHandler((request, response, deniedException) -> {
                            ApiError error = deniedException instanceof CsrfException
                                    ? ApiError.of("csrf_failed",
                                            "Your session has expired. Reload the page and try again.")
                                    : ApiError.of("forbidden", "You don't have access to that");
                            writeError(objectMapper, response, HttpStatus.FORBIDDEN, error);
                        }))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    private static void writeError(
            ObjectMapper objectMapper,
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            ApiError error) throws java.io.IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(secureCookies));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Applied by {@code AuthController} after a successful manual login: rotate
     * the session id (session fixation) and the CSRF token.
     */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    AuthenticationManager authenticationManager(
            AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Report "bad credentials" rather than "no such user", so the API does
        // not confirm which usernames exist.
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
