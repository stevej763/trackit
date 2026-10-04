package dev.stevejones.trackit.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Counts attempts per key in a fixed window that starts at the first attempt.
 * In memory on purpose: there is one api instance, and losing the counts on a
 * restart is harmless.
 */
final class AttemptCounter {

    /** Past this many keys, expired windows are swept out on the next write. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(Instant startedAt, int attempts) {
    }

    AttemptCounter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /** How long until this key may try again, or empty if it may now. */
    Optional<Duration> blockedFor(String key) {
        Window current = windows.get(key);
        Instant now = clock.instant();
        if (current == null || current.attempts() < limit || expired(current, now)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, current.startedAt().plus(window)));
    }

    void record(String key) {
        Instant now = clock.instant();
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.values().removeIf(existing -> expired(existing, now));
        }
        windows.compute(key, (ignored, current) -> current == null || expired(current, now)
                ? new Window(now, 1)
                : new Window(current.startedAt(), current.attempts() + 1));
    }

    void reset(String key) {
        windows.remove(key);
    }

    void clear() {
        windows.clear();
    }

    private boolean expired(Window existing, Instant now) {
        return !now.isBefore(existing.startedAt().plus(window));
    }
}
