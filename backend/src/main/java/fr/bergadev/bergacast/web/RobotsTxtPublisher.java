package fr.bergadev.bergacast.web;

import fr.bergadev.bergacast.storage.MediaKeys;
import fr.bergadev.bergacast.storage.ObjectStorage;
import fr.bergadev.bergacast.storage.ObjectStorageException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Writes the robots.txt of {@link RobotsController} at the root of the bucket on startup, so that search engines keep
 * away from the feeds and audio files too (the storage cannot add the {@code X-Robots-Tag} header). Cheap and
 * idempotent; a failure is only logged, the next startup writes it again.
 */
@Component
class RobotsTxtPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(RobotsTxtPublisher.class);

    private final ObjectStorage storage;

    RobotsTxtPublisher(ObjectStorage storage) {
        this.storage = storage;
    }

    @EventListener(ApplicationReadyEvent.class)
    void publish() {
        try {
            storage.put(
                    MediaKeys.ROBOTS_TXT,
                    RobotsController.ROBOTS_TXT.getBytes(StandardCharsets.UTF_8),
                    MediaType.TEXT_PLAIN_VALUE,
                    RobotsController.ONE_DAY);
        } catch (ObjectStorageException exception) {
            LOGGER.warn("robots.txt not written to the object storage", exception);
        }
    }
}
