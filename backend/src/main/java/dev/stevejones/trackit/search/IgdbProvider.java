package dev.stevejones.trackit.search;

import com.fasterxml.jackson.databind.JsonNode;
import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Games from IGDB.
 *
 * <p>IGDB takes its own query language in a plain-text POST body rather than
 * query parameters, and authenticates with a Twitch app token (see
 * {@link IgdbTokenStore}).
 */
@Component
public class IgdbProvider implements MetadataProvider {

    private static final int SEARCH_LIMIT = 20;
    private static final Pattern NUMERIC_ID = Pattern.compile("\\d{1,19}");
    private static final String DETAIL_FIELDS =
            "name,summary,first_release_date,cover.image_id,screenshots.image_id,platforms.name,genres.name";

    private final IgdbProperties properties;
    private final IgdbTokenStore tokenStore;
    private final RestClient restClient;

    public IgdbProvider(
            IgdbProperties properties,
            IgdbTokenStore tokenStore,
            RestClient.Builder providerRestClientBuilder) {
        this.properties = properties;
        this.tokenStore = tokenStore;
        this.restClient = providerRestClientBuilder.clone().baseUrl(properties.baseUrl()).build();
    }

    @Override
    public boolean supports(MediaType mediaType) {
        return mediaType == MediaType.GAME;
    }

    @Override
    public List<SearchResult> search(MediaType mediaType, String query) {
        String body = "search \"%s\"; fields name,summary,first_release_date,cover.image_id; limit %d;"
                .formatted(escape(query), SEARCH_LIMIT);

        JsonNode games = postQuery(body);
        List<SearchResult> results = new ArrayList<>();
        for (JsonNode game : games) {
            results.add(new SearchResult(
                    MetadataSource.IGDB,
                    MediaType.GAME,
                    game.path("id").asText(),
                    text(game, "name") != null ? text(game, "name") : "Untitled",
                    releaseYear(game),
                    text(game, "summary"),
                    coverUrl(game),
                    null));
        }
        return results;
    }

    @Override
    public MediaItem fetchDetails(MediaType mediaType, String externalId) {
        if (!NUMERIC_ID.matcher(externalId).matches()) {
            // Ids go straight into the query body, so only ever accept digits.
            throw new ProviderException("That doesn't look like an IGDB game id.");
        }

        JsonNode games = postQuery("where id = %s; fields %s; limit 1;".formatted(externalId, DETAIL_FIELDS));
        if (games.isEmpty()) {
            throw new ProviderException("IGDB doesn't have a game with that id.");
        }
        JsonNode game = games.get(0);

        MediaItem item = new MediaItem();
        item.setSource(MetadataSource.IGDB);
        item.setMediaType(MediaType.GAME);
        item.setExternalId(externalId);
        item.setTitle(text(game, "name") != null ? text(game, "name") : "Untitled");
        item.setReleaseYear(releaseYear(game));
        item.setOverview(text(game, "summary"));
        item.setPosterUrl(coverUrl(game));
        item.setBackdropUrl(screenshotUrl(game));
        item.setPlatforms(names(game.path("platforms")));
        item.setGenres(names(game.path("genres")));
        item.setMetadataFetchedAt(Instant.now());
        return item;
    }

    /**
     * A single request for every seed: IGDB expands {@code similar_games} for a
     * whole set of ids at once, and returns ten per game.
     */
    @Override
    public Map<String, List<SearchResult>> recommendationsFor(MediaType mediaType, List<String> externalIds) {
        List<String> ids = externalIds.stream().filter(id -> NUMERIC_ID.matcher(id).matches()).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        String body = "where id = (%s); fields id, similar_games.name, similar_games.summary,"
                .formatted(String.join(",", ids))
                + " similar_games.first_release_date, similar_games.cover.image_id;"
                + " limit %d;".formatted(ids.size());

        Map<String, List<SearchResult>> bySeed = new LinkedHashMap<>();
        for (JsonNode seed : postQuery(body)) {
            List<SearchResult> similar = new ArrayList<>();
            for (JsonNode game : seed.path("similar_games")) {
                similar.add(new SearchResult(
                        MetadataSource.IGDB,
                        MediaType.GAME,
                        game.path("id").asText(),
                        text(game, "name") != null ? text(game, "name") : "Untitled",
                        releaseYear(game),
                        text(game, "summary"),
                        coverUrl(game),
                        null));
            }
            if (!similar.isEmpty()) {
                bySeed.put(seed.path("id").asText(), similar);
            }
        }
        return bySeed;
    }

    /** Posts a query, refreshing the token once if IGDB says it has expired. */
    private JsonNode postQuery(String query) {
        if (!properties.configured()) {
            throw new ProviderException(
                    "Game search is not configured. Set IGDB_CLIENT_ID and IGDB_CLIENT_SECRET, "
                            + "or add the game by hand.");
        }

        try {
            return execute(query, tokenStore.token());
        } catch (HttpClientErrorException.Unauthorized ex) {
            // Token revoked or expired early; one retry with a fresh one.
            return execute(query, tokenStore.refresh());
        }
    }

    private JsonNode execute(String query, String token) {
        try {
            JsonNode body = restClient.post()
                    .uri("/games")
                    .header("Client-ID", properties.clientId())
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body(query)
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null || !body.isArray()) {
                throw new ProviderException("IGDB returned an unexpected response. Try again in a moment.");
            }
            return body;
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw ex; // handled by postQuery, which retries once
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new ProviderException("IGDB is rate-limiting us. Try again in a moment.", ex);
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new ProviderException("IGDB rejected that search. Try different wording.", ex);
        } catch (RestClientException ex) {
            throw new ProviderException("Couldn't reach IGDB. Check the server's network access.", ex);
        }
    }

    /** IGDB reports release dates as unix seconds. */
    private static Integer releaseYear(JsonNode game) {
        JsonNode value = game.get("first_release_date");
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return Instant.ofEpochSecond(value.asLong()).atZone(ZoneOffset.UTC).getYear();
    }

    private String coverUrl(JsonNode game) {
        String imageId = text(game.path("cover"), "image_id");
        return imageId == null ? null : properties.coverBaseUrl() + "/" + imageId + ".jpg";
    }

    private String screenshotUrl(JsonNode game) {
        JsonNode screenshots = game.path("screenshots");
        if (!screenshots.isArray() || screenshots.isEmpty()) {
            return null;
        }
        String imageId = text(screenshots.get(0), "image_id");
        return imageId == null ? null : properties.screenshotBaseUrl() + "/" + imageId + ".jpg";
    }

    private static List<String> names(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) {
            return null;
        }
        List<String> names = StreamSupport.stream(array.spliterator(), false)
                .map(node -> text(node, "name"))
                .filter(name -> name != null)
                .toList();
        return names.isEmpty() ? null : names;
    }

    /** Keeps user input from breaking out of the quoted search term. */
    private static String escape(String query) {
        return query.replace("\\", "").replace("\"", "").replace(";", "");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String asText = value.asText().trim();
        return asText.isEmpty() ? null : asText;
    }
}
