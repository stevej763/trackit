package dev.stevejones.trackit.recommend;

import dev.stevejones.trackit.media.MediaType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationCacheRepository extends JpaRepository<RecommendationCache, Long> {

    Optional<RecommendationCache> findByUserIdAndMediaType(Long userId, MediaType mediaType);
}
