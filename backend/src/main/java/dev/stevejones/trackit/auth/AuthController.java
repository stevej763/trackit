package dev.stevejones.trackit.auth;

import dev.stevejones.trackit.auth.AuthDtos.LoginRequest;
import dev.stevejones.trackit.auth.AuthDtos.RegisterRequest;
import dev.stevejones.trackit.auth.AuthDtos.UserResponse;
import dev.stevejones.trackit.common.ConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and login. Logout is handled by Spring Security's own logout
 * filter (see SecurityConfig), which clears the context and the Postgres-backed
 * session for us.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextHolderStrategy holderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthController(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    /** Open registration, then log the new account straight in. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        AppUser user;
        try {
            user = users.save(new AppUser(request.username(), passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException ex) {
            // The unique index is the real arbiter, so no check-then-act race.
            throw new ConflictException("That username is already taken");
        }

        establishSession(request.username(), request.password(), httpRequest, httpResponse);
        return new UserResponse(user.getId(), user.getUsername());
    }

    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Authentication authentication =
                establishSession(request.username(), request.password(), httpRequest, httpResponse);
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return new UserResponse(principal.getId(), principal.getUsername());
    }

    /**
     * The SPA calls this on boot to decide whether to show the login page. It
     * requires authentication, so an anonymous caller gets a 401 ApiError from
     * the configured entry point rather than a body to parse.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return new UserResponse(principal.getId(), principal.getUsername());
    }

    /**
     * Authenticates, applies session-fixation and CSRF-token rotation, and
     * persists the security context into the session.
     */
    private Authentication establishSession(
            String username, String password, HttpServletRequest request, HttpServletResponse response) {

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        renderRotatedCsrfToken(request);

        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        holderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return authentication;
    }

    /**
     * Rotating the CSRF token on login swaps the request attribute for a fresh
     * deferred token, which only writes its cookie once something reads its
     * value. CsrfCookieFilter already ran and read the <em>old</em> one, so
     * without this read the client would be left holding a token the server has
     * discarded and its next write would be rejected.
     */
    private static void renderRotatedCsrfToken(HttpServletRequest request) {
        CsrfToken rotated = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (rotated != null) {
            rotated.getToken();
        }
    }
}
