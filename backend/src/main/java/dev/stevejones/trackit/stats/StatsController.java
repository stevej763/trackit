package dev.stevejones.trackit.stats;

import dev.stevejones.trackit.auth.AppUserPrincipal;
import dev.stevejones.trackit.common.BadRequestException;
import java.time.ZoneId;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    /**
     * @param tz the browser's IANA zone, which decides which month a finish
     *           falls in. UTC when absent.
     */
    @GetMapping
    public StatsResponse stats(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(name = "tz", required = false) String tz) {
        return service.forUser(principal.getId(), zoneOf(tz));
    }

    /** One calendar year of finishes, bounded by midnight on 1 January in {@code tz}. */
    @GetMapping("/years/{year}")
    public YearInReview year(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable int year,
            @RequestParam(name = "tz", required = false) String tz) {
        if (year < 1 || year > 9998) {
            throw new BadRequestException("'" + year + "' is not a year we can count.");
        }
        return service.yearInReview(principal.getId(), year, zoneOf(tz));
    }

    /**
     * Region ids only ("Europe/London"). Java would also accept an offset like
     * "+05:00", but Postgres reads those with POSIX's inverted sign, so the
     * two would silently disagree.
     */
    private static ZoneId zoneOf(String tz) {
        if (tz == null || tz.isBlank()) {
            return ZoneId.of("UTC"); // not ZoneOffset.UTC, whose id "Z" Postgres doesn't know
        }
        if (!ZoneId.getAvailableZoneIds().contains(tz)) {
            throw new BadRequestException("'" + tz + "' is not a time zone. Try one like Europe/London.");
        }
        return ZoneId.of(tz);
    }
}
