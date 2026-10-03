package dev.stevejones.trackit.search;

/**
 * A metadata provider is misconfigured, rate-limited or unreachable.
 *
 * <p>The message is written to be shown to the user as-is (it becomes a 503
 * ApiError), so it must name the fix and never leak a stack trace or a key.
 */
public class ProviderException extends RuntimeException {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
