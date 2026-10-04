package dev.stevejones.trackit.auth;

import dev.stevejones.trackit.common.FieldException;
import dev.stevejones.trackit.common.NotFoundException;
import dev.stevejones.trackit.media.MediaItemRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Changing your password and closing your account. */
@Service
public class AccountService {

    private final AppUserRepository users;
    private final MediaItemRepository mediaItems;
    private final PasswordEncoder passwordEncoder;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final LoginThrottle throttle;

    public AccountService(
            AppUserRepository users,
            MediaItemRepository mediaItems,
            PasswordEncoder passwordEncoder,
            FindByIndexNameSessionRepository<? extends Session> sessions,
            LoginThrottle throttle) {
        this.users = users;
        this.mediaItems = mediaItems;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.throttle = throttle;
    }

    /**
     * Sets a new password and signs out every other session, which is usually
     * why someone changes it. The session making the change stays signed in.
     */
    @Transactional
    public void changePassword(
            Long userId, String currentPassword, String newPassword, String address, String currentSessionId) {

        AppUser user = confirmPassword(userId, currentPassword, "currentPassword", address);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        endSessions(user.getUsername(), currentSessionId);
    }

    /**
     * Deletes the account and everything that belongs only to it. Entries and
     * cached suggestions go by cascade; hand-typed catalogue rows have to be
     * removed first, since nothing else could ever point at them and the
     * cascade runs the other way. Shared provider rows stay for other users.
     */
    @Transactional
    public void deleteAccount(Long userId, String password, String address) {
        AppUser user = confirmPassword(userId, password, "password", address);
        mediaItems.deleteManualItemsTrackedBy(userId);
        users.delete(user);
        endSessions(user.getUsername(), null);
    }

    /**
     * The same throttle as signing in: a stolen session shouldn't be a way to
     * guess the password at full speed.
     */
    private AppUser confirmPassword(Long userId, String password, String field, String address) {
        AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("No such account"));
        throttle.checkSignIn(user.getUsername(), address);
        if (!matches(password, user.getPasswordHash())) {
            throttle.signInFailed(user.getUsername(), address);
            throw new FieldException(field, "That isn't your current password");
        }
        return user;
    }

    /**
     * BCrypt reads at most 72 bytes and Spring's encoder refuses anything
     * longer, so no stored hash can match a longer password anyway.
     */
    private boolean matches(String raw, String hash) {
        return raw.getBytes(StandardCharsets.UTF_8).length <= 72 && passwordEncoder.matches(raw, hash);
    }

    /** Deletes the user's stored sessions, except the one given, if any. */
    private void endSessions(String username, String keepSessionId) {
        sessions.findByPrincipalName(username).keySet().stream()
                .filter(id -> !id.equals(keepSessionId))
                .forEach(sessions::deleteById);
    }
}
