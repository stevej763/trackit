package dev.stevejones.trackit.recommend;

import dev.stevejones.trackit.auth.AppUserPrincipal;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.recommend.RecommendationDtos.RecommendationsResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping
    public RecommendationsResponse recommendations(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam("type") MediaType type,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {

        return service.forUser(principal.getId(), type, refresh);
    }
}
