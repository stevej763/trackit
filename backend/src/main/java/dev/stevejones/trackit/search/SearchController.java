package dev.stevejones.trackit.search;

import dev.stevejones.trackit.auth.AppUserPrincipal;
import dev.stevejones.trackit.entry.EntryRepository;
import dev.stevejones.trackit.entry.EntryRepository.TrackedExternalId;
import dev.stevejones.trackit.media.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Provider search. Results are normalised and never persisted. */
@RestController
@RequestMapping("/api/search")
@Validated
public class SearchController {

    private final MetadataProviders providers;
    private final EntryRepository entries;

    public SearchController(MetadataProviders providers, EntryRepository entries) {
        this.providers = providers;
        this.entries = entries;
    }

    @GetMapping
    public List<SearchResult> search(
            @RequestParam("q") @NotBlank @Size(min = 2, max = 200) String query,
            @RequestParam("type") MediaType type,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        List<SearchResult> results = providers.forType(type).search(type, query.trim());
        return markAlreadyTracked(results, type, principal.getId());
    }

    /**
     * Annotates each hit with the caller's existing entry id, so the UI can say
     * "already tracked" and link to it rather than offering a duplicate add.
     */
    private List<SearchResult> markAlreadyTracked(List<SearchResult> results, MediaType type, Long userId) {
        if (results.isEmpty()) {
            return results;
        }

        List<String> externalIds = results.stream().map(SearchResult::externalId).toList();
        Map<String, Long> trackedByExternalId = new HashMap<>();
        for (TrackedExternalId tracked : entries.findTrackedExternalIds(
                userId, results.get(0).source(), type, externalIds)) {
            trackedByExternalId.put(tracked.getExternalId(), tracked.getEntryId());
        }

        if (trackedByExternalId.isEmpty()) {
            return results;
        }
        return results.stream()
                .map(result -> result.withTrackedEntryId(trackedByExternalId.get(result.externalId())))
                .toList();
    }
}
