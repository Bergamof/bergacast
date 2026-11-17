package fr.bergadev.bergacast.storage;

import java.time.Duration;
import org.springframework.http.CacheControl;

/** {@code Cache-Control} values of the objects written to the storage. */
public final class MediaCacheControl {

    /** RSS feed of a podcast: apps poll it, a short freshness keeps changes visible soon. */
    public static final CacheControl FEED =
            CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    /** Cover images of podcasts and episodes: as fresh as the feed, which links them. */
    public static final CacheControl COVER_IMAGE = FEED;

    /** Audio files: the URL contains the episode id and the file is never replaced. */
    public static final CacheControl AUDIO =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    /** Permanent redirections: clients may remember them, a day bounds how long a misconfigured target would stick. */
    public static final CacheControl REDIRECT =
            CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    private MediaCacheControl() {}
}
