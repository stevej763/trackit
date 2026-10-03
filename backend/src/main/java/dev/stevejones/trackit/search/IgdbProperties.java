package dev.stevejones.trackit.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Config for IGDB (games), which authenticates through Twitch. */
@ConfigurationProperties(prefix = "trackit.igdb")
public record IgdbProperties(
        String clientId,
        String clientSecret,
        String baseUrl,
        String tokenUrl,
        String coverBaseUrl,
        String screenshotBaseUrl) {

    public boolean configured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
