package dev.stevejones.trackit.entry;

import dev.stevejones.trackit.media.MediaItemResponse;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request and response bodies for /api/entries. */
public final class EntryDtos {

    private EntryDtos() {
    }

    /**
     * Start tracking a title. Either reference a provider result
     * ({@code source} + {@code externalId}) or supply {@code manual} to type one
     * in by hand.
     */
    public record CreateEntryRequest(
            @NotNull MediaType mediaType,
            @NotNull EntryStatus status,
            MetadataSource source,
            String externalId,
            @Valid ManualItem manual) {
    }

    public record ManualItem(
            @NotBlank @Size(max = 300) String title,
            @Min(1850) @Max(2200) Integer releaseYear,
            @Size(max = 5000) String overview,
            @Size(max = 2000) String posterUrl,
            @Size(max = 2000) String backdropUrl,
            Integer runtimeMinutes,
            Integer seasonCount,
            Integer episodeCount,
            List<String> platforms,
            List<String> genres) {
    }

    /**
     * Replaces every editable field. Null clears a value, so the client sends
     * the whole editable set rather than a partial patch.
     */
    public record UpdateEntryRequest(
            @NotNull EntryStatus status,
            @Min(1) @Max(10) Integer rating,
            @Size(max = 20000) String review,
            LocalDate startedOn,
            LocalDate finishedOn) {
    }

    /** The grid's quick status change. */
    public record UpdateStatusRequest(@NotNull EntryStatus status) {
    }

    public record EntryResponse(
            Long id,
            EntryStatus status,
            Integer rating,
            String review,
            LocalDate startedOn,
            LocalDate finishedOn,
            Instant createdAt,
            Instant updatedAt,
            MediaItemResponse mediaItem) {

        public static EntryResponse from(Entry entry) {
            return new EntryResponse(
                    entry.getId(),
                    entry.getStatus(),
                    entry.getRating(),
                    entry.getReview(),
                    entry.getStartedOn(),
                    entry.getFinishedOn(),
                    entry.getCreatedAt(),
                    entry.getUpdatedAt(),
                    MediaItemResponse.from(entry.getMediaItem()));
        }
    }

    /** A page of entries, flattened so the SPA doesn't depend on Spring's Page shape. */
    public record EntryPage(
            List<EntryResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {
    }
}
