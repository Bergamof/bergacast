package fr.bergadev.bergacast.web;

import fr.bergadev.bergacast.config.BergacastProperties;
import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds the absolute public URLs advertised to podcast clients, based on {@code BERGACAST_PUBLIC_BASE_URL}. */
@Component
public class PublicUrls {

    private final URI baseUrl;

    public PublicUrls(BergacastProperties properties) {
        this.baseUrl = properties.publicBaseUrl();
    }

    public URI base() {
        return baseUrl;
    }

    public URI feed(String podcastSlug) {
        return URI.create(baseUrl + "/" + podcastSlug + ".xml");
    }

    public URI publish(String podcastSlug) {
        return URI.create(baseUrl + "/api/v1/" + podcastSlug + "/episodes");
    }

    public URI audio(String podcastSlug, UUID episodeId, String fileExtension) {
        return URI.create(baseUrl + "/" + podcastSlug + "/episodes/" + episodeId + "/audio." + fileExtension);
    }
}
