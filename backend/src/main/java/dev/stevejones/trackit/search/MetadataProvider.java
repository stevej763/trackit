package dev.stevejones.trackit.search;

import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaType;
import java.util.List;
import java.util.Map;

/** A source of catalogue metadata. One implementation per external API. */
public interface MetadataProvider {

    boolean supports(MediaType mediaType);

    /** Normalised search hits. Never persisted. */
    List<SearchResult> search(MediaType mediaType, String query);

    /**
     * The full record for one title, as an unsaved {@link MediaItem}.
     *
     * @throws ProviderException if the id is unknown or the provider fails
     */
    MediaItem fetchDetails(MediaType mediaType, String externalId);

    /**
     * Titles each provider thinks are like the given ones, keyed by the seed
     * external id that produced them.
     *
     * <p>Takes every seed at once rather than one at a time because IGDB expands
     * {@code similar_games} for a whole set of ids in a single request, while
     * TMDB needs one call per title. A batch signature lets the efficient
     * provider actually be efficient.
     *
     * <p>A seed the provider knows nothing about is simply absent from the
     * result; it is not an error.
     */
    Map<String, List<SearchResult>> recommendationsFor(MediaType mediaType, List<String> externalIds);
}
