package dev.stevejones.trackit.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.stevejones.trackit.common.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-04T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    private void fail(String username, String address, int times) {
        for (int i = 0; i < times; i++) {
            throttle.signInFailed(username, address);
        }
    }

    @Test
    void blocksAUsernameAfterRepeatedFailuresWhateverTheCase() {
        fail("steve", "10.0.0.1", LoginThrottle.FAILURES_PER_USERNAME);

        // From a different address, and typed differently: still the same account.
        assertThatThrownBy(() -> throttle.checkSignIn("STEVE", "10.0.0.2"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("15 minutes");
        assertThatCode(() -> throttle.checkSignIn("anna", "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void liftsTheBlockWhenTheWindowEnds() {
        fail("steve", "10.0.0.1", LoginThrottle.FAILURES_PER_USERNAME);
        clock.advance(LoginThrottle.FAILURE_WINDOW);

        assertThatCode(() -> throttle.checkSignIn("steve", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void blocksAnAddressTryingManyUsernames() {
        for (int i = 0; i < LoginThrottle.FAILURES_PER_ADDRESS; i++) {
            throttle.signInFailed("user" + i, "10.0.0.1");
        }

        assertThatThrownBy(() -> throttle.checkSignIn("someone-new", "10.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> throttle.checkSignIn("someone-new", "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessClearsTheUsernameButNotTheAddress() {
        fail("steve", "10.0.0.1", LoginThrottle.FAILURES_PER_USERNAME - 1);
        throttle.signInSucceeded("steve");
        fail("steve", "10.0.0.1", LoginThrottle.FAILURES_PER_USERNAME - 1);
        assertThatCode(() -> throttle.checkSignIn("steve", "10.0.0.1")).doesNotThrowAnyException();

        // Signing in to your own account mustn't reset the count of guesses
        // you've made at everyone else's from the same address.
        for (int i = 0; i < LoginThrottle.FAILURES_PER_ADDRESS; i++) {
            throttle.signInFailed("victim" + i, "10.0.0.9");
        }
        throttle.signInSucceeded("attacker");
        assertThatThrownBy(() -> throttle.checkSignIn("attacker", "10.0.0.9"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void limitsSignUpsPerAddress() {
        for (int i = 0; i < LoginThrottle.SIGNUPS_PER_ADDRESS; i++) {
            throttle.checkSignUp("10.0.0.1");
        }

        assertThatThrownBy(() -> throttle.checkSignUp("10.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("new accounts");
        clock.advance(LoginThrottle.SIGNUP_WINDOW);
        assertThatCode(() -> throttle.checkSignUp("10.0.0.1")).doesNotThrowAnyException();
    }
}
