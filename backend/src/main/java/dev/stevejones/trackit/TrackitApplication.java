package dev.stevejones.trackit;

import dev.stevejones.trackit.search.IgdbProperties;
import dev.stevejones.trackit.search.TmdbProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({TmdbProperties.class, IgdbProperties.class})
@EnableScheduling
public class TrackitApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackitApplication.class, args);
    }
}
