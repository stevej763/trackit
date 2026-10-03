package dev.stevejones.trackit.common;

import java.util.Map;

/**
 * The one error shape every failing API call returns.
 *
 * @param error   stable machine-readable code, e.g. {@code not_found}
 * @param message human-readable text the SPA can show as-is
 * @param details field name to message, for validation failures; null otherwise
 */
public record ApiError(String error, String message, Map<String, String> details) {

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, null);
    }
}
