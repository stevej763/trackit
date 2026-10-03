package dev.stevejones.trackit.common;

/** Maps to a 404. Also used when a row exists but belongs to another user. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
