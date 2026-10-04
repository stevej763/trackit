package dev.stevejones.trackit.auth;

import dev.stevejones.trackit.common.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing and sign-up spam.
 *
 * <p>Failed sign-ins are counted per username, which stops a guesser working
 * through one account from many addresses, and per address, which stops one
 * address working through many accounts. The address limit is the looser of the
 * two because a household or an office shares one. A successful sign-in clears
 * that username's count but never the address's, or one valid account would
 * let its owner keep guessing at everyone else's.
 *
 * <p>Throttling, not lockout: the block lifts by itself when the window ends,
 * so someone guessing at your username can delay you but not lock you out.
 */
@Component
public class LoginThrottle {

    static final int FAILURES_PER_USERNAME = 5;
    static final int FAILURES_PER_ADDRESS = 20;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final int SIGNUPS_PER_ADDRESS = 5;
    static final Duration SIGNUP_WINDOW = Duration.ofHours(1);

    private final AttemptCounter failuresByUsername;
    private final AttemptCounter failuresByAddress;
    private final AttemptCounter signupsByAddress;

    @Autowired
    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        failuresByUsername = new AttemptCounter(FAILURES_PER_USERNAME, FAILURE_WINDOW, clock);
        failuresByAddress = new AttemptCounter(FAILURES_PER_ADDRESS, FAILURE_WINDOW, clock);
        signupsByAddress = new AttemptCounter(SIGNUPS_PER_ADDRESS, SIGNUP_WINDOW, clock);
    }

    /**
     * Called before checking a password, so a throttled guess is refused
     * whether or not it would have been right.
     */
    public void checkSignIn(String username, String address) {
        refuseIfBlocked(failuresByAddress.blockedFor(address)
                .or(() -> failuresByUsername.blockedFor(key(username))), "sign-in attempts");
    }

    public void signInFailed(String username, String address) {
        failuresByUsername.record(key(username));
        failuresByAddress.record(address);
    }

    public void signInSucceeded(String username) {
        failuresByUsername.reset(key(username));
    }

    /** Counts every sign-up, successful or not: each one costs a BCrypt hash. */
    public void checkSignUp(String address) {
        refuseIfBlocked(signupsByAddress.blockedFor(address), "new accounts from here");
        signupsByAddress.record(address);
    }

    /** Forgets everything. For tests, which share one application context. */
    public void clear() {
        failuresByUsername.clear();
        failuresByAddress.clear();
        signupsByAddress.clear();
    }

    /** Usernames are case-insensitive (citext), so their counts must be too. */
    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static void refuseIfBlocked(Optional<Duration> blockedFor, String what) {
        blockedFor.ifPresent(wait -> {
            long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
            throw new TooManyRequestsException(
                    "Too many " + what + ". Try again in " + minutes + (minutes == 1 ? " minute." : " minutes."),
                    wait);
        });
    }
}
