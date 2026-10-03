package dev.stevejones.trackit.common;

/** Maps to a 400 for request problems bean validation can't express. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
