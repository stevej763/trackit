package dev.stevejones.trackit.search;

import dev.stevejones.trackit.media.MediaItem;
import dev.stevejones.trackit.media.MediaType;
import java.util.List;

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
}
