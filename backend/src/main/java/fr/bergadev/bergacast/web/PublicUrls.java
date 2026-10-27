package fr.bergadev.bergacast.web;

import fr.bergadev.bergacast.config.BergacastProperties;
import fr.bergadev.bergacast.storage.MediaKeys;
import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds the absolute public URLs advertised to podcast clients: the API on {@code BERGACAST_PUBLIC_BASE_URL}, the
 * objects of the storage (feeds, audio files, images) on {@code BERGACAST_MEDIA_BASE_URL}.
 */
@Component
public class PublicUrls {

    private final URI baseUrl;
    private final URI mediaBaseUrl;

    public PublicUrls(BergacastProperties properties) {
        this.baseUrl = properties.publicBaseUrl();
        this.mediaBaseUrl = properties.mediaBaseUrl();
    }

    public URI base() {
        return baseUrl;
    }

    public URI publish(String podcastSlug) {
        return URI.create(baseUrl + "/api/v1/" + podcastSlug + "/episodes");
    }

    public URI feed(String podcastSlug) {
        return media(MediaKeys.feed(podcastSlug));
    }

    public URI audio(String podcastSlug, UUID episodeId, String fileExtension) {
        return media(MediaKeys.episodeAudio(podcastSlug, episodeId, fileExtension));
    }

    public URI podcastImage(String podcastSlug, String fileExtension) {
        return media(MediaKeys.podcastImage(podcastSlug, fileExtension));
    }

    /** Public URL of the object stored with {@code key}. */
    public URI media(String key) {
        return URI.create(mediaBaseUrl + "/" + key);
    }
}
