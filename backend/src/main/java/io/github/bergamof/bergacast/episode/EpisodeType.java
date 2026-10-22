package io.github.bergamof.bergacast.episode;

/** Type of an episode, published as {@code itunes:episodeType}. */
public enum EpisodeType {
    FULL,
    TRAILER,
    BONUS;

    /** Value of the {@code itunes:episodeType} element. */
    public String itunesValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
