package fr.bergadev.bergacast.storage;

import java.time.Duration;
import org.springframework.http.CacheControl;

/** {@code Cache-Control} values of the objects written to the storage. */
public final class MediaCacheControl {

    /**
     * Cover images of podcasts and episodes: as fresh as the feed, which links them, since their URL stays the same
     * when they are replaced.
     */
    public static final CacheControl COVER_IMAGE =
            CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private MediaCacheControl() {}
}
