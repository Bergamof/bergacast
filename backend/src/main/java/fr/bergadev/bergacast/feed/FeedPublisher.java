package fr.bergadev.bergacast.feed;

import fr.bergadev.bergacast.podcast.PodcastFeedChanged;
import fr.bergadev.bergacast.storage.AfterCommit;
import fr.bergadev.bergacast.storage.MediaKeys;
import fr.bergadev.bergacast.storage.ObjectStorage;
import fr.bergadev.bergacast.storage.ObjectStorageException;
import fr.bergadev.bergacast.web.PublicUrls;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.event.EventListener;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes the RSS feed of a podcast to the object storage, where podcast apps read it, each time what it shows changes.
 *
 * <p>The feed is a snapshot: episodes scheduled in the future appear the next time it is written after their
 * publication date. Writes of the feed of a podcast are serialized, so that an older snapshot never replaces a newer
 * one; this lock is local to the process, which is enough with a single instance of the backend.
 */
@Service
public class FeedPublisher {

    static final String RSS_XML_VALUE = "application/rss+xml;charset=UTF-8";
    /** Podcast apps poll feeds: a short freshness keeps changes visible soon. */
    static final CacheControl FIVE_MINUTES =
            CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final FeedService feedService;
    private final ObjectStorage storage;
    private final PublicUrls publicUrls;
    private final Clock clock;
    private final TransactionTemplate newReadOnlyTransaction;
    private final ConcurrentMap<String, Lock> locks = new ConcurrentHashMap<>();

    FeedPublisher(
            FeedService feedService,
            ObjectStorage storage,
            PublicUrls publicUrls,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.feedService = feedService;
        this.storage = storage;
        this.publicUrls = publicUrls;
        this.clock = clock;
        // A new transaction: the feed is also written from after-commit callbacks, where the committed transaction
        // is still bound to the thread and cannot be joined.
        this.newReadOnlyTransaction = new TransactionTemplate(transactionManager);
        this.newReadOnlyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.newReadOnlyTransaction.setReadOnly(true);
    }

    /**
     * Writes the feed again once the change is committed; a failure reaches the caller of the change as a
     * {@link FeedPublicationException}.
     */
    @EventListener
    void onFeedChanged(PodcastFeedChanged event) {
        AfterCommit.run(() -> publish(event.podcastSlug()));
    }

    /**
     * Writes the feed of a podcast from the current data.
     *
     * @throws fr.bergadev.bergacast.web.ResourceNotFoundException when there is no such podcast
     * @throws FeedPublicationException when the feed cannot be written to the object storage
     */
    public FeedPublication publish(String slug) {
        Lock lock = locks.computeIfAbsent(slug, key -> new ReentrantLock());
        lock.lock();
        try {
            RenderedFeed feed =
                    Objects.requireNonNull(newReadOnlyTransaction.execute(status -> feedService.render(slug)));
            storage.put(MediaKeys.feed(slug), feed.document(), RSS_XML_VALUE, FIVE_MINUTES);
            return new FeedPublication(publicUrls.feed(slug), clock.instant(), feed.episodeCount());
        } catch (ObjectStorageException exception) {
            throw new FeedPublicationException(slug, exception);
        } finally {
            lock.unlock();
        }
    }
}
