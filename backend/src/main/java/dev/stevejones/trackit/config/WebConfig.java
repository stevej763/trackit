package dev.stevejones.trackit.config;

import dev.stevejones.trackit.entry.EntryStatus;
import dev.stevejones.trackit.media.MediaType;
import dev.stevejones.trackit.media.MetadataSource;
import java.util.Locale;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Accept enum query parameters in any case, so the SPA can keep its URLs
 * lower-case (?type=movie&status=in_progress) while Java keeps its convention.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, MediaType.class,
                value -> MediaType.valueOf(normalise(value)));
        registry.addConverter(String.class, EntryStatus.class,
                value -> EntryStatus.valueOf(normalise(value)));
        registry.addConverter(String.class, MetadataSource.class,
                value -> MetadataSource.valueOf(normalise(value)));
    }

    private static String normalise(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
