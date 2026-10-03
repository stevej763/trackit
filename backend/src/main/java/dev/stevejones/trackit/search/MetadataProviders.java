package dev.stevejones.trackit.search;

import dev.stevejones.trackit.media.MediaType;
import java.util.List;
import org.springframework.stereotype.Component;

/** Picks the provider that handles a given media type. */
@Component
public class MetadataProviders {

    private final List<MetadataProvider> providers;

    public MetadataProviders(List<MetadataProvider> providers) {
        this.providers = providers;
    }

    public MetadataProvider forType(MediaType mediaType) {
        return providers.stream()
                .filter(provider -> provider.supports(mediaType))
                .findFirst()
                .orElseThrow(() -> new ProviderException("Nothing can look up " + mediaType + " titles."));
    }
}
