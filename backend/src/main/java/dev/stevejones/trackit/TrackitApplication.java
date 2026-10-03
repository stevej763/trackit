package dev.stevejones.trackit;

import dev.stevejones.trackit.search.IgdbProperties;
import dev.stevejones.trackit.search.TmdbProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({TmdbProperties.class, IgdbProperties.class})
public class TrackitApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackitApplication.class, args);
    }
}
