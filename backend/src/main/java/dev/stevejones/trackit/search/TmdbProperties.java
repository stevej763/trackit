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
}
