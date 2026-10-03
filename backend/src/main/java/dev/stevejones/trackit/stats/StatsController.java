package dev.stevejones.trackit.stats;

import dev.stevejones.trackit.auth.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping
    public StatsResponse stats(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.forUser(principal.getId());
    }
}
