package dev.stevejones.trackit.search;

import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;

/**
 * One provider hit, normalised across TMDB and IGDB. Nothing is persisted at
 * search time; {@code externalId} is what the client posts back to start
 * tracking a title.
 *
 * @param trackedEntryId the caller's existing entry for this title, or null if
 *                       they aren't tracking it yet
 */
public record SearchResult(
        MetadataSource source,
        MediaType mediaType,
        String externalId,
        String title,
        Integer releaseYear,
        String overview,
        String posterUrl,
        Long trackedEntryId) {

    public SearchResult withTrackedEntryId(Long entryId) {
        return new SearchResult(
                source, mediaType, externalId, title, releaseYear, overview, posterUrl, entryId);
    }
}
