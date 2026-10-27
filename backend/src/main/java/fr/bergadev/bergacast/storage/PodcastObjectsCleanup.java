package fr.bergadev.bergacast.storage;

import fr.bergadev.bergacast.podcast.PodcastDeleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Removes the objects of a deleted podcast once its deletion is committed, so that a podcast created later with the
 * same slug starts from an empty folder. A failure only leaves orphan objects behind (logged).
 */
@Component
class PodcastObjectsCleanup {

    private final ObjectStorage storage;

    PodcastObjectsCleanup(ObjectStorage storage) {
        this.storage = storage;
    }

    @EventListener
    void onPodcastDeleted(PodcastDeleted event) {
        String slug = event.podcastSlug();
        AfterCommit.run(() -> {
            storage.discard(MediaKeys.feed(slug));
            storage.discardAll(MediaKeys.podcastFolder(slug));
        });
    }
}
