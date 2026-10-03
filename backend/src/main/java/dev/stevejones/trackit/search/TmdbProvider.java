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
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/** Movies and TV shows from The Movie Database. */
@Component
public class TmdbProvider implements MetadataProvider {

    private static final int SEARCH_LIMIT = 20;

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
     * skipped, and a failure on one seed doesn't lose the others.
     */
    @Override
    public Map<String, List<SearchResult>> recommendationsFor(MediaType mediaType, List<String> externalIds) {
        String segment = mediaType == MediaType.MOVIE ? "/movie/" : "/tv/";
        Map<String, List<SearchResult>> bySeed = new LinkedHashMap<>();

        for (String externalId : externalIds) {
            JsonNode body = get(segment + externalId + "/recommendations", Map.of("page", "1"));
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
        return bySeed;
    }

    private JsonNode get(String path, Map<String, String> params) {
        if (!properties.configured()) {
            throw new ProviderException(
                    "Film and TV search is not configured. Set TMDB_API_KEY, or add the title by hand.");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(path)
                .queryParam("api_key", properties.apiKey())
                .queryParam("language", "en-US");
        params.forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();

        try {
            JsonNode body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
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
            throw new ProviderException("Couldn't reach TMDB. Check the server's network access.", ex);
        }
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
