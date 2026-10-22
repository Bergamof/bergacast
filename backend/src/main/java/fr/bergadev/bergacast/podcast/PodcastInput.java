package fr.bergadev.bergacast.podcast;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import org.hibernate.validator.constraints.URL;
import org.jspecify.annotations.Nullable;

/**
 * Writable state of a podcast, used as the body of a creation and as the target of a JSON Merge Patch.
 *
 * @param slug URL identifier, immutable once the podcast is created
 * @param title channel title
 * @param description channel description
 * @param author {@code itunes:author}
 * @param ownerName name in {@code itunes:owner}
 * @param ownerEmail email in {@code itunes:owner}
 * @param language BCP 47 language tag of the content, e.g. {@code fr} or {@code en-US}
 * @param category Apple Podcasts category
 * @param explicit whether the podcast contains explicit content (default {@code false})
 * @param imageUrl URL of the cover art
 * @param websiteUrl URL of the podcast website, used as the channel link
 * @param feed feed settings
 */
public record PodcastInput(
        @NotBlank @Size(max = Slugs.MAX_LENGTH) @Pattern(regexp = Slugs.PATTERN) @NotReservedSlug
        String slug,

        @NotBlank @Size(max = PodcastInput.MAX_TEXT_LENGTH) String title,
        @NotBlank String description,
        @Size(max = PodcastInput.MAX_TEXT_LENGTH) @Nullable String author,
        @Size(max = PodcastInput.MAX_TEXT_LENGTH) @Nullable String ownerName,

        @Email @Size(max = PodcastInput.MAX_TEXT_LENGTH) @Nullable
        String ownerEmail,

        @NotBlank @Pattern(regexp = PodcastInput.LANGUAGE_TAG_PATTERN)
        String language,

        @Size(max = PodcastInput.MAX_TEXT_LENGTH) @Nullable String category,
        @Nullable Boolean explicit,

        @URL @Size(max = PodcastInput.MAX_URL_LENGTH) @Nullable
        String imageUrl,

        @URL @Size(max = PodcastInput.MAX_URL_LENGTH) @Nullable
        String websiteUrl,

        @Valid @Nullable FeedSettingsInput feed) {

    static final int MAX_TEXT_LENGTH = 255;
    static final int MAX_URL_LENGTH = 2048;
    static final String LANGUAGE_TAG_PATTERN = "^[A-Za-z]{2,3}(-[A-Za-z0-9]{1,8})*$";

    static PodcastInput from(Podcast podcast) {
        PodcastDetails details = podcast.getDetails();
        return new PodcastInput(
                podcast.getSlug(),
                details.title(),
                details.description(),
                details.author(),
                details.ownerName(),
                details.ownerEmail(),
                details.language(),
                details.category(),
                details.explicit(),
                details.imageUrl(),
                details.websiteUrl(),
                FeedSettingsInput.from(podcast.getFeedSettings()));
    }

    PodcastDetails toDetails() {
        return new PodcastDetails(
                title,
                description,
                author,
                ownerName,
                ownerEmail,
                language,
                category,
                Objects.requireNonNullElse(explicit, false),
                imageUrl,
                websiteUrl);
    }

    FeedSettings toFeedSettings() {
        return feed == null ? FeedSettings.defaults() : feed.toFeedSettings();
    }
}
