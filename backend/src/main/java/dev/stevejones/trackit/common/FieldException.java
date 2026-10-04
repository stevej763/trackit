package dev.stevejones.trackit.common;

/**
 * Maps to a 400 in the same shape as a bean-validation failure, for a field
 * the server can only check against stored state (a current password, say).
 */
public class FieldException extends RuntimeException {

    private final String field;

    public FieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
