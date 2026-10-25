package fr.bergadev.bergacast.podcast;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import org.hibernate.validator.constraints.URL;
import org.jspecify.annotations.Nullable;

/** Writable state of a podcast, used as the body of a creation and as the target of a JSON Merge Patch. */
@Schema(
        description = "Writable state of a podcast: body of a creation, or target of a JSON Merge Patch. Most fields "
                + "are published in the channel of the RSS feed.")
public record PodcastInput(
        @Schema(
                description = "URL identifier of the podcast, used in the feed URL (`/{slug}.xml`), the audio "
                        + "URLs and the publication URL. Lowercase letters, digits and single hyphens; some "
                        + "names used by the application (api, admin, podcasts...) are reserved. Immutable "
                        + "once the podcast is created.",
                example = "my-podcast")
        @NotBlank
        @Size(max = Slugs.MAX_LENGTH)
        @Pattern(regexp = Slugs.PATTERN)
        @NotReservedSlug
        String slug,

        @Schema(description = "Title of the podcast (channel `title`).", example = "My Podcast")
        @NotBlank
        @Size(max = PodcastInput.MAX_TEXT_LENGTH)
        String title,

        @Schema(
                description = "Description of the podcast (channel `description`).",
                example = "Conversations about self-hosting, open source and the open web.")
        @NotBlank
        String description,

        @Schema(description = "Author shown by podcast apps (`itunes:author`).", example = "Jane Doe", nullable = true)
        @Size(max = PodcastInput.MAX_TEXT_LENGTH)
        @Nullable
        String author,

        @Schema(description = "Name of the owner (`itunes:owner`).", example = "Jane Doe", nullable = true)
        @Size(max = PodcastInput.MAX_TEXT_LENGTH)
        @Nullable
        String ownerName,

        @Schema(
                description =
                        "Contact email of the owner (`itunes:owner`), used by directories to verify " + "ownership.",
                example = "jane@example.com",
                nullable = true)
        @Email
        @Size(max = PodcastInput.MAX_TEXT_LENGTH)
        @Nullable
        String ownerEmail,

        @Schema(description = "BCP 47 language tag of the content (channel `language`).", example = "en-US")
        @NotBlank
        @Pattern(regexp = PodcastInput.LANGUAGE_TAG_PATTERN)
        String language,

        @Schema(description = "Apple Podcasts category (`itunes:category`).", example = "Technology", nullable = true)
        @Size(max = PodcastInput.MAX_TEXT_LENGTH)
        @Nullable
        String category,

        @Schema(
                description = "Whether the podcast contains explicit content (`itunes:explicit`). Absent or "
                        + "null means false.",
                defaultValue = "false",
                example = "false",
                nullable = true)
        @Nullable
        Boolean explicit,

        @Schema(
                description = "URL of the cover art (`itunes:image`); Apple Podcasts expects a square JPEG or "
                        + "PNG of 1400 to 3000 pixels.",
                example = "https://example.com/cover.jpg",
                nullable = true)
        @URL
        @Size(max = PodcastInput.MAX_URL_LENGTH)
        @Nullable
        String imageUrl,

        @Schema(
                description = "URL of the podcast website (channel `link`). Defaults to the public base URL "
                        + "of the server.",
                example = "https://example.com",
                nullable = true)
        @URL
        @Size(max = PodcastInput.MAX_URL_LENGTH)
        @Nullable
        String websiteUrl,

        @Schema(
                description = "Rules selecting and ordering the episodes of the feed. Absent or null: all published "
                        + "episodes, newest first.",
                nullable = true)
        @Valid
        @Nullable
        FeedSettingsInput feed) {

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
