package dev.stevejones.trackit;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for tests that need the real schema. Flyway runs against a throwaway
 * Postgres, so the migrations are exercised on every run - including the citext
 * extension and the jsonb columns, neither of which an in-memory database would
 * reproduce.
 *
 * <p>Deliberately the singleton-container pattern rather than JUnit's
 * {@code @Testcontainers}/{@code @Container}: that pair stops the container when
 * the first test class finishes, which leaves every class after it talking to a
 * dead database. Started once in a static initialiser and never stopped, the
 * container is reaped by Testcontainers' own Ryuk sidecar when the JVM exits.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
