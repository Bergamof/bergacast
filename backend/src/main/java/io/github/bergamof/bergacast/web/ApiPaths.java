package io.github.bergamof.bergacast.web;

/** URI templates of the REST API, shared by controllers (mappings), links and security rules. */
public final class ApiPaths {

    public static final String BASE = "/api/v1";
    public static final String ACCESS_TOKENS = BASE + "/access-tokens";
    public static final String PODCASTS = BASE + "/podcasts";
    public static final String PODCAST = PODCASTS + "/{slug}";
    public static final String PODCAST_API_TOKENS = PODCAST + "/api-tokens";
    public static final String PODCAST_API_TOKEN = PODCAST_API_TOKENS + "/{tokenId}";
    public static final String EPISODES = BASE + "/{slug}/episodes";
    public static final String EPISODE = EPISODES + "/{episodeId}";
    public static final String ADMIN_USERS = BASE + "/admin-users";

    /**
     * Mapping of {@link #EPISODES}: the slug excludes the collections of the API so that, e.g.,
     * {@code /api/v1/podcasts/episodes} unambiguously addresses the podcast {@code episodes}.
     */
    public static final String EPISODES_MAPPING =
            BASE + "/{slug:(?!(?:podcasts|admin-users|access-tokens)$)[a-z0-9-]+}/episodes";

    public static final String EPISODE_MAPPING = EPISODES_MAPPING + "/{episodeId}";
    public static final String ADMIN_USER = ADMIN_USERS + "/{adminUserId}";

    private ApiPaths() {}
}
