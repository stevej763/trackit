package dev.stevejones.trackit.search;

/**
 * The provider answered, and has no such title: removed since, or never
 * existed. Unlike its parent, trying again later won't help.
 */
public class UnknownTitleException extends ProviderException {

    public UnknownTitleException(String message) {
        super(message);
    }

    public UnknownTitleException(String message, Throwable cause) {
        super(message, cause);
    }
}
