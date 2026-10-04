package dev.stevejones.trackit.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Config for The Movie Database (movies and TV). */
@ConfigurationProperties(prefix = "trackit.tmdb")
public record TmdbProperties(
        String apiKey,
        String baseUrl,
        String posterBaseUrl,
        String backdropBaseUrl) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * TMDB issues two credentials: a v3 "API Key" (32 hex characters), which
     * only works as a query parameter, and a v4 "API Read Access Token", a JWT
     * that goes in a Bearer header and so never appears in a URL. Either works
     * in TMDB_API_KEY; the token is preferred.
     */
    public boolean usesReadAccessToken() {
        return configured() && apiKey.startsWith("eyJ");
    }
}
