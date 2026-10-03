package dev.stevejones.trackit.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security defers CSRF token loading, so the XSRF-TOKEN cookie is only
 * written once something asks for the token's value. Touching it on every
 * request means the SPA's first call (GET /api/auth/me on boot) is enough to
 * seed the cookie before it ever needs to POST.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Rendering the token is the side effect we want here.
            csrfToken.getToken();
        }
        chain.doFilter(request, response);
    }
}
