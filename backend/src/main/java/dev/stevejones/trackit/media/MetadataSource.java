package dev.stevejones.trackit.media;

/** Where a {@link MediaItem}'s metadata came from. */
public enum MetadataSource {
    TMDB,
    IGDB,
    /** Typed in by hand, for anything the providers don't have (or when no API keys are configured). */
    MANUAL
}
