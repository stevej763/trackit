package dev.stevejones.trackit.recommend;

import dev.stevejones.trackit.media.MediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A computed set of recommendations, held per user and media type.
 *
 * <p>The payload is the serialised response and nothing ever queries inside it,
 * so it stays an opaque string rather than a mapped jsonb structure.
 */
@Entity
@Table(name = "recommendation_cache")
public class RecommendationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(name = "seed_fingerprint", nullable = false)
    private String seedFingerprint;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    protected RecommendationCache() {
        // for JPA
    }

    public RecommendationCache(Long userId, MediaType mediaType, String seedFingerprint, String payload) {
        this.userId = userId;
        this.mediaType = mediaType;
        this.seedFingerprint = seedFingerprint;
        this.payload = payload;
        this.computedAt = Instant.now();
    }

    public void replaceWith(String seedFingerprint, String payload) {
        this.seedFingerprint = seedFingerprint;
        this.payload = payload;
        this.computedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSeedFingerprint() {
        return seedFingerprint;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public String getPayload() {
        return payload;
    }
}
