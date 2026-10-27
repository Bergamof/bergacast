package fr.bergadev.bergacast.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaKeysTest {

    private static final UUID EPISODE_ID = UUID.fromString("3f2c8a1e-5b7d-4c9e-8f10-0123456789ab");

    @Test
    void storesTheFeedAtTheRootOfTheBucket() {
        assertThat(MediaKeys.feed("my-podcast")).isEqualTo("my-podcast.xml");
    }

    @Test
    void storesTheImageInTheFolderOfThePodcast() {
        assertThat(MediaKeys.podcastImage("my-podcast", "png")).isEqualTo("my-podcast/image.png");
    }

    @Test
    void storesTheAudioInTheFolderOfItsEpisode() {
        assertThat(MediaKeys.episodeAudio("my-podcast", EPISODE_ID, "mp3"))
                .isEqualTo("my-podcast/episodes/3f2c8a1e-5b7d-4c9e-8f10-0123456789ab/audio.mp3");
    }

    /** The folder prefix ends with a slash: it never matches the objects of a podcast whose slug extends this one. */
    @Test
    void groupsEveryObjectOfAPodcastButItsFeedUnderItsFolder() {
        String folder = MediaKeys.podcastFolder("my-podcast");

        assertThat(folder).isEqualTo("my-podcast/");
        assertThat(MediaKeys.podcastImage("my-podcast", "jpg")).startsWith(folder);
        assertThat(MediaKeys.episodeAudio("my-podcast", EPISODE_ID, "m4a")).startsWith(folder);
        assertThat(MediaKeys.feed("my-podcast")).doesNotStartWith(folder);
        assertThat(MediaKeys.podcastImage("my-podcast-2", "jpg")).doesNotStartWith(folder);
    }
}
