package fr.bergadev.bergacast.storage;

import java.util.UUID;

/**
 * Keys of the objects stored for the podcasts. A key is also the path of the object's public URL below the media base
 * URL, and is derived from the podcast slug (and episode id) alone: any object can be found without the database.
 */
public final class MediaKeys {

    private MediaKeys() {}

    /** RSS feed of a podcast, e.g. {@code my-podcast.xml}. */
    public static String feed(String podcastSlug) {
        return podcastSlug + ".xml";
    }

    /** Cover image of a podcast, e.g. {@code my-podcast/image.jpg}. */
    public static String podcastImage(String podcastSlug, String fileExtension) {
        return podcastFolder(podcastSlug) + "image." + fileExtension;
    }

    /** Audio file of an episode, e.g. {@code my-podcast/episodes/3f2c…/audio.mp3}. */
    public static String episodeAudio(String podcastSlug, UUID episodeId, String fileExtension) {
        return podcastFolder(podcastSlug) + "episodes/" + episodeId + "/audio." + fileExtension;
    }

    /** Prefix of every object of a podcast but its feed. */
    public static String podcastFolder(String podcastSlug) {
        return podcastSlug + "/";
    }
}
