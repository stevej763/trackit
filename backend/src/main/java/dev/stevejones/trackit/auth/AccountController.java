package dev.stevejones.trackit.auth;

import dev.stevejones.trackit.auth.AuthDtos.ChangePasswordRequest;
import dev.stevejones.trackit.auth.AuthDtos.DeleteAccountRequest;
import dev.stevejones.trackit.entry.EntryDtos.LibraryExport;
import dev.stevejones.trackit.entry.EntryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own account: password, export, deletion. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accounts;
    private final EntryService entries;
    private final CsrfTokenRepository csrfTokenRepository;

    public AccountController(
            AccountService accounts, EntryService entries, CsrfTokenRepository csrfTokenRepository) {
        this.accounts = accounts;
        this.entries = entries;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {

        HttpSession session = httpRequest.getSession(false);
        accounts.changePassword(
                principal.getId(),
                request.currentPassword(),
                request.newPassword(),
                httpRequest.getRemoteAddr(),
                session == null ? null : session.getId());
    }

    /**
     * Everything in the library, as one JSON file. A download rather than a
     * page of results: it exists so your data can leave with you.
     */
    @GetMapping("/export")
    public ResponseEntity<LibraryExport> export(@AuthenticationPrincipal AppUserPrincipal principal) {
        String filename = "trackit-" + principal.getUsername() + "-" + LocalDate.now(ZoneOffset.UTC) + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(entries.export(principal.getId(), principal.getUsername()));
    }

    /**
     * Deletes the account and signs this browser out, the same way logging out
     * does: including a fresh CSRF token, since the next request is likely to
     * be a sign-up or sign-in POST.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        accounts.deleteAccount(principal.getId(), request.password(), httpRequest.getRemoteAddr());

        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);
        csrfTokenRepository.loadDeferredToken(httpRequest, httpResponse).get().getToken();
    }
}
