package dev.stevejones.trackit.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies for /api/auth. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 32, message = "Username must be 3-32 characters")
            @Pattern(
                    regexp = "^[A-Za-z0-9._-]+$",
                    message = "Username may only contain letters, numbers, dots, underscores and hyphens")
            String username,

            @NotBlank
            @Size(min = 8, max = 200, message = "Password must be at least 8 characters")
            String password) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record UserResponse(Long id, String username) {
    }
}
