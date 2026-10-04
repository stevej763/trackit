package dev.stevejones.trackit.search;

import com.fasterxml.jackson.databind.JsonNode;
import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/** Movies and TV shows from The Movie Database. */
@Component
public class TmdbProvider implements MetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(TmdbProvider.class);
    private static final int SEARCH_LIMIT = 20;
    /** TMDB ids are plain integers; anything else would rewrite the request path. */
    private static final Pattern NUMERIC_ID = Pattern.compile("\\d{1,10}");

    private final TmdbProperties properties;
    private final RestClient restClient;

    public TmdbProvider(TmdbProperties properties, RestClient.Builder providerRestClientBuilder) {
        this.properties = properties;
        // Clone: RestClient.Builder mutates in place, and this bean is shared.
        this.restClient = providerRestClientBuilder.clone().build();
    }

    @Override
    public boolean supports(MediaType mediaType) {
        return mediaType == MediaType.MOVIE || mediaType == MediaType.TV;
    }

    @Override
    public List<SearchResult> search(MediaType mediaType, String query) {
        JsonNode body = get(searchPath(mediaType), Map.of(
                "query", query,
                "include_adult", "false",
                "page", "1"));

        List<SearchResult> results = new ArrayList<>();
        for (JsonNode node : body.path("results")) {
            results.add(new SearchResult(
                    MetadataSource.TMDB,
                    mediaType,
                    node.path("id").asText(),
                    title(node, mediaType),
                    year(node, mediaType),
                    text(node, "overview"),
                    imageUrl(properties.posterBaseUrl(), text(node, "poster_path")),
                    null));
            if (results.size() == SEARCH_LIMIT) {
                break;
            }
        }
        return results;
    }

    @Override
    public MediaItem fetchDetails(MediaType mediaType, String externalId) {
        if (!NUMERIC_ID.matcher(externalId).matches()) {
            // Ids become a path segment, so "550/credits" or "1/../../tv/1399"
            // would fetch something else and file it under this id.
            throw new ProviderException("That doesn't look like a TMDB id.");
        }
        String path = (mediaType == MediaType.MOVIE ? "/movie/" : "/tv/") + externalId;
        JsonNode node = get(path, Map.of());

        MediaItem item = new MediaItem();
        item.setSource(MetadataSource.TMDB);
        item.setMediaType(mediaType);
        item.setExternalId(externalId);
        item.setTitle(title(node, mediaType));
        item.setReleaseYear(year(node, mediaType));
        item.setOverview(text(node, "overview"));
        item.setPosterUrl(imageUrl(properties.posterBaseUrl(), text(node, "poster_path")));
        item.setBackdropUrl(imageUrl(properties.backdropBaseUrl(), text(node, "backdrop_path")));
        item.setGenres(names(node.path("genres")));
        item.setMetadataFetchedAt(Instant.now());

        if (mediaType == MediaType.MOVIE) {
            item.setRuntimeMinutes(integer(node, "runtime"));
        } else {
            item.setSeasonCount(integer(node, "number_of_seasons"));
            item.setEpisodeCount(integer(node, "number_of_episodes"));
        }
        return item;
    }

    /**
     * One call per seed: TMDB has no batch form. Seeds it has nothing for are
     * skipped, and a failure on one seed doesn't lose the others: a title TMDB
     * has since removed would otherwise break suggestions until its score
     * dropped. Only when every seed fails is there nothing to show, and then
     * the first failure is reported.
     */
    @Override
    public Map<String, List<SearchResult>> recommendationsFor(MediaType mediaType, List<String> externalIds) {
        requireConfigured();
        String segment = mediaType == MediaType.MOVIE ? "/movie/" : "/tv/";
        Map<String, List<SearchResult>> bySeed = new LinkedHashMap<>();
        List<String> ids = externalIds.stream().filter(id -> NUMERIC_ID.matcher(id).matches()).toList();
        ProviderException firstFailure = null;
        int failures = 0;

        for (String externalId : ids) {
            JsonNode body;
            try {
                body = get(segment + externalId + "/recommendations", Map.of("page", "1"));
            } catch (ProviderException ex) {
                log.warn("Skipping recommendations for TMDB {} {}: {}", mediaType, externalId, ex.getMessage());
                firstFailure = firstFailure == null ? ex : firstFailure;
                failures++;
                continue;
            }
            List<SearchResult> results = new ArrayList<>();

            for (JsonNode node : body.path("results")) {
                // The recommendations endpoint takes no include_adult parameter,
                // unlike search, so the filtering has to happen here.
                if (node.path("adult").asBoolean(false)) {
                    continue;
                }
                results.add(new SearchResult(
                        MetadataSource.TMDB,
                        mediaType,
                        node.path("id").asText(),
                        title(node, mediaType),
                        year(node, mediaType),
                        text(node, "overview"),
                        imageUrl(properties.posterBaseUrl(), text(node, "poster_path")),
                        null));
            }
            if (!results.isEmpty()) {
                bySeed.put(externalId, results);
            }
        }
        if (failures > 0 && failures == ids.size()) {
            throw firstFailure;
        }
        return bySeed;
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new ProviderException(
                    "Film and TV search is not configured. Set TMDB_API_KEY, or add the title by hand.");
        }
    }

    private JsonNode get(String path, Map<String, String> params) {
        requireConfigured();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(path)
                .queryParam("language", "en-US");
        if (!properties.usesReadAccessToken()) {
            // A v3 key can only travel in the query string; see withoutUrl.
            builder.queryParam("api_key", properties.apiKey());
        }
        params.forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();

        try {
            JsonNode body = restClient.get()
                    .uri(uri)
                    .headers(headers -> {
                        if (properties.usesReadAccessToken()) {
                            headers.setBearerAuth(properties.apiKey());
                        }
                    })
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new ProviderException("TMDB returned an empty response. Try again in a moment.");
            }
            return body;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            throw new ProviderException("TMDB rejected the API key. Check TMDB_API_KEY.", ex);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProviderException("TMDB doesn't have that title any more.", ex);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new ProviderException("TMDB is rate-limiting us. Try again in a moment.", ex);
        } catch (RestClientException ex) {
            throw new ProviderException("Couldn't reach TMDB. Check the server's network access.", withoutUrl(ex));
        }
    }

    /**
     * A transport failure's message quotes the full request URL, which holds a
     * v3 api_key, and the cause of a ProviderException is logged. The
     * underlying I/O error says what went wrong without the URL.
     */
    private static Throwable withoutUrl(RestClientException ex) {
        return ex instanceof ResourceAccessException && ex.getCause() != null ? ex.getCause() : ex;
    }

    private static String searchPath(MediaType mediaType) {
        return mediaType == MediaType.MOVIE ? "/search/movie" : "/search/tv";
    }

    /** TMDB calls it "title" for films and "name" for shows. */
    private static String title(JsonNode node, MediaType mediaType) {
        String field = mediaType == MediaType.MOVIE ? "title" : "name";
        String value = text(node, field);
        return value != null ? value : "Untitled";
    }

    private static Integer year(JsonNode node, MediaType mediaType) {
        String field = mediaType == MediaType.MOVIE ? "release_date" : "first_air_date";
        String date = text(node, field);
        if (date == null || date.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> names(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) {
            return null;
        }
        return StreamSupport.stream(array.spliterator(), false)
                .map(node -> text(node, "name"))
                .filter(name -> name != null)
                .toList();
    }

    private static String imageUrl(String base, String path) {
        return path == null ? null : base + path;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String asText = value.asText().trim();
        return asText.isEmpty() ? null : asText;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : value.asInt();
    }
}
