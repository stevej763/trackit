package dev.stevejones.trackit.search;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Holds the Twitch client-credentials token IGDB requires.
 *
 * <p>Tokens last ~60 days, so this caches one in memory and refreshes it a
 * minute before expiry, or on demand when IGDB rejects it with a 401.
 */
@Component
public class IgdbTokenStore {

    private static final Logger log = LoggerFactory.getLogger(IgdbTokenStore.class);
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);

    private final IgdbProperties properties;
    private final RestClient restClient;

    private volatile String token;
    private volatile Instant expiresAt = Instant.EPOCH;

    public IgdbTokenStore(IgdbProperties properties, RestClient.Builder providerRestClientBuilder) {
        this.properties = properties;
        this.restClient = providerRestClientBuilder.clone().build();
    }

    /** A valid token, fetching one if the cache is empty or stale. */
    public synchronized String token() {
        if (token != null && Instant.now().isBefore(expiresAt)) {
            return token;
        }
        return refresh();
    }

    /** Discards the cached token and fetches a new one. */
    public synchronized String refresh() {
        if (!properties.configured()) {
            throw new ProviderException(
                    "Game search is not configured. Set IGDB_CLIENT_ID and IGDB_CLIENT_SECRET, "
                            + "or add the game by hand.");
        }

        URI uri = UriComponentsBuilder.fromUriString(properties.tokenUrl())
                .queryParam("client_id", properties.clientId())
                .queryParam("client_secret", properties.clientSecret())
                .queryParam("grant_type", "client_credentials")
                .build()
                .encode()
                .toUri();

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            token = null;
            expiresAt = Instant.EPOCH;
            throw new ProviderException(
                    "Could not authenticate with IGDB. Check IGDB_CLIENT_ID and IGDB_CLIENT_SECRET.", ex);
        }

        if (response == null || !response.hasNonNull("access_token")) {
            throw new ProviderException("IGDB returned no access token. Check IGDB_CLIENT_ID and IGDB_CLIENT_SECRET.");
        }

        token = response.get("access_token").asText();
        long expiresIn = response.path("expires_in").asLong(3600);
        expiresAt = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
        log.info("Obtained IGDB access token, valid until {}", expiresAt);
        return token;
    }
}
