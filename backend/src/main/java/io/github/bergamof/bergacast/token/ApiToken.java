package io.github.bergamof.bergacast.token;

import io.github.bergamof.bergacast.podcast.Podcast;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A long-lived token allowing an automated tool to publish episodes of one podcast. Only its hash is stored. */
@Entity
@Table(name = "api_token")
public class ApiToken {

    static final int MAX_NAME_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "podcast_id", nullable = false, updatable = false)
    private Podcast podcast;

    @Column(name = "name", nullable = false, length = ApiToken.MAX_NAME_LENGTH)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "token_prefix", nullable = false, updatable = false, length = ApiTokens.DISPLAYED_PREFIX_LENGTH)
    private String tokenPrefix;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private @Nullable Instant lastUsedAt;

    protected ApiToken() {
        // required by JPA
    }

    ApiToken(Podcast podcast, String name, String tokenHash, String tokenPrefix, Instant createdAt) {
        this.podcast = podcast;
        this.name = name;
        this.tokenHash = tokenHash;
        this.tokenPrefix = tokenPrefix;
        this.createdAt = createdAt;
    }

    void markUsed(Instant now) {
        this.lastUsedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Podcast getPodcast() {
        return podcast;
    }

    public String getName() {
        return name;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
