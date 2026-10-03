package dev.stevejones.trackit.common;

/** Maps to a 409: the request is valid but collides with existing state. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
