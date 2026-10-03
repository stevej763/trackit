package dev.stevejones.trackit.media;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    Optional<MediaItem> findBySourceAndMediaTypeAndExternalId(
            MetadataSource source, MediaType mediaType, String externalId);
}
