package io.github.bergamof.bergacast.podcast;

/** Date used to order episodes in a feed, both to evict the oldest ones and to sort them. */
public enum EpisodeOrdering {
    /** Date the episode is (or was) published, chosen by the publisher. */
    PUBLICATION_DATE,
    /** Date the episode was uploaded to Bergacast. */
    ADDED_DATE
}
