package fr.bergadev.bergacast.web;

/**
 * Keeps search engines away: the podcasts are meant for the subscribers who know their feed URL. Every response
 * carries {@link #X_ROBOTS_TAG} (see the security configuration), besides the static {@code /robots.txt}
 * ({@code src/main/resources/static}). Podcast apps fetch feeds and audio regardless.
 */
public final class RobotsHeaders {

    public static final String X_ROBOTS_TAG = "X-Robots-Tag";
    public static final String NO_INDEX = "noindex, nofollow";

    private RobotsHeaders() {}
}
