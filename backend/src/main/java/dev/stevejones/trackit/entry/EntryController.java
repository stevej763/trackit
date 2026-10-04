package dev.stevejones.trackit.entry;

import dev.stevejones.trackit.auth.AppUserPrincipal;
import dev.stevejones.trackit.common.BadRequestException;
import dev.stevejones.trackit.entry.EntryDtos.CreateEntryRequest;
import dev.stevejones.trackit.entry.EntryDtos.EntryPage;
import dev.stevejones.trackit.entry.EntryDtos.EntryResponse;
import dev.stevejones.trackit.entry.EntryDtos.ManualItem;
import dev.stevejones.trackit.entry.EntryDtos.UpdateEntryRequest;
import dev.stevejones.trackit.entry.EntryDtos.UpdateStatusRequest;
import dev.stevejones.trackit.media.MediaType;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The library: everything the signed-in user is tracking. */
@RestController
@RequestMapping("/api/entries")
public class EntryController {

    private static final int MAX_PAGE_SIZE = 100;

    /** Whitelist, so a sort parameter can't reach arbitrary entity paths. */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "added", "createdAt",
            "updated", "updatedAt",
            "title", "mediaItem.title",
            "rating", "rating",
            "finished", "finishedAt",
            "year", "mediaItem.releaseYear");

    private final EntryService service;

    public EntryController(EntryService service) {
        this.service = service;
    }

    @GetMapping
    public EntryPage list(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) MediaType type,
            @RequestParam(required = false) EntryStatus status,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "added") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "48") int size) {

        Page<Entry> result = service.list(
                principal.getId(),
                type,
                status,
                minRating,
                q,
                sortProperty(sort),
                "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC,
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE));
        return new EntryPage(
                result.getContent().stream().map(EntryResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/{id}")
    public EntryResponse get(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        return EntryResponse.from(service.get(principal.getId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntryResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreateEntryRequest request) {
        return EntryResponse.from(service.create(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public EntryResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateEntryRequest request) {
        return EntryResponse.from(service.update(principal.getId(), id, request));
    }

    @PatchMapping("/{id}/status")
    public EntryResponse updateStatus(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return EntryResponse.from(service.updateStatus(principal.getId(), id, request.status()));
    }

    /** Corrects a hand-typed title's details. Replaces every field, like PUT /{id}. */
    @PutMapping("/{id}/details")
    public EntryResponse updateDetails(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ManualItem details) {
        return EntryResponse.from(service.updateDetails(principal.getId(), id, details));
    }

    /** Fetches a provider title's details again: new seasons, a firmed-up release year. */
    @PostMapping("/{id}/refresh")
    public EntryResponse refreshDetails(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        return EntryResponse.from(service.refreshDetails(principal.getId(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        service.delete(principal.getId(), id);
    }

    /** Resolves a client sort key to an entity path, rejecting anything else. */
    private static String sortProperty(String sort) {
        String property = SORT_FIELDS.get(sort.toLowerCase());
        if (property == null) {
            throw new BadRequestException("Unknown sort '" + sort + "'. Try one of " + SORT_FIELDS.keySet());
        }
        return property;
    }
}
