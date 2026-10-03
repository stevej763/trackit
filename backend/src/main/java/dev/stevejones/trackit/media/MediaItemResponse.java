package dev.stevejones.trackit.media;

import java.util.List;

/** The catalogue half of what the SPA renders for an entry. */
public record MediaItemResponse(
        Long id,
        MediaType mediaType,
        MetadataSource source,
        String externalId,
        String title,
        Integer releaseYear,
        String overview,
        String posterUrl,
        String backdropUrl,
        Integer runtimeMinutes,
        Integer seasonCount,
        Integer episodeCount,
        List<String> platforms,
        List<String> genres) {

    public static MediaItemResponse from(MediaItem item) {
        return new MediaItemResponse(
                item.getId(),
                item.getMediaType(),
                item.getSource(),
                item.getExternalId(),
                item.getTitle(),
                item.getReleaseYear(),
                item.getOverview(),
                item.getPosterUrl(),
                item.getBackdropUrl(),
                item.getRuntimeMinutes(),
                item.getSeasonCount(),
                item.getEpisodeCount(),
                item.getPlatforms(),
                item.getGenres());
    }
}
