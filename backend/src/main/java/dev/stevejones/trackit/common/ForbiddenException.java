package dev.stevejones.trackit.common;

/**
 * Maps to a 403 for something this server doesn't allow at all, with a code
 * the SPA can act on. Not for another user's data: that is a 404.
 */
public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
