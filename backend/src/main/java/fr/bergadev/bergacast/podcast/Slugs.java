package fr.bergadev.bergacast.podcast;

import java.util.Set;

/** Rules of podcast slugs, which appear at the root of public URLs ({@code /{slug}.xml}). */
public final class Slugs {

    public static final int MAX_LENGTH = 64;
    public static final String PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /** Path segments used by the application itself, which would make a podcast unreachable or shadow a route. */
    public static final Set<String> RESERVED = Set.of(
            "api",
            "admin",
            "swagger-ui",
            "v3",
            "actuator",
            "podcasts",
            "admin-users",
            "access-tokens",
            "assets",
            "favicon.ico");

    private Slugs() {}

    public static boolean isReserved(String slug) {
        return RESERVED.contains(slug);
    }
}
