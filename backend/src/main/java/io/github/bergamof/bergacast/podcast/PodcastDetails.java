package io.github.bergamof.bergacast.podcast;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;

/** Descriptive metadata of a podcast, published in the channel of its feed. */
@Embeddable
public record PodcastDetails(
        @Column(name = "title", nullable = false) String title,

        @Column(name = "description", nullable = false, columnDefinition = "text")
        String description,

        @Column(name = "author") @Nullable String author,
        @Column(name = "owner_name") @Nullable String ownerName,
        @Column(name = "owner_email") @Nullable String ownerEmail,

        @Column(name = "language", nullable = false, length = 35)
        String language,

        @Column(name = "category") @Nullable String category,
        @Column(name = "explicit", nullable = false) boolean explicit,
        @Column(name = "image_url", length = 2048) @Nullable String imageUrl,

        @Column(name = "website_url", length = 2048) @Nullable
        String websiteUrl) {}
