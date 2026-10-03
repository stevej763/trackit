package dev.stevejones.trackit.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Shared by the metadata providers. Short timeouts matter here: a hanging
     * TMDB or IGDB call would otherwise tie up a request thread and leave the
     * user watching a spinner.
     *
     * <p>Providers are handed the builder rather than a finished client so each
     * can clone it and set its own base URL, and so tests can bind a
     * MockRestServiceServer to it.
     */
    @Bean
    RestClient.Builder providerRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory((ClientHttpRequestFactory) factory);
    }
}
