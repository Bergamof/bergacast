package fr.bergadev.bergacast.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.bergadev.bergacast.storage.ObjectStorage;
import fr.bergadev.bergacast.storage.ObjectStorageException;
import fr.bergadev.bergacast.web.PublicUrls;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.transaction.PlatformTransactionManager;

class FeedPublisherTest {

    private static final long TIMEOUT_SECONDS = 5;
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    private final FeedService feedService = mock(FeedService.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final PublicUrls publicUrls = mock(PublicUrls.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private FeedPublisher publisher;

    @BeforeEach
    void createPublisher() {
        when(feedService.render(anyString())).thenReturn(new RenderedFeed(new byte[] {1}, 3));
        when(publicUrls.feed(anyString())).thenReturn(URI.create("https://media.example.test/feed.xml"));
        publisher = new FeedPublisher(
                feedService,
                storage,
                publicUrls,
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(PlatformTransactionManager.class));
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void writesTheRenderedFeedAndReportsIt() {
        FeedPublication publication = publisher.publish("demo");

        assertThat(publication.feedUrl()).hasToString("https://media.example.test/feed.xml");
        assertThat(publication.publishedAt()).isEqualTo(NOW);
        assertThat(publication.episodeCount()).isEqualTo(3);
    }

    @Test
    void neverWritesTheFeedOfTheSamePodcastTwiceAtTheSameTime() throws Exception {
        AtomicInteger concurrentWrites = new AtomicInteger();
        AtomicInteger highestConcurrency = new AtomicInteger();
        CountDownLatch firstWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
                    highestConcurrency.accumulateAndGet(concurrentWrites.incrementAndGet(), Math::max);
                    firstWriteStarted.countDown();
                    releaseFirstWrite.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    concurrentWrites.decrementAndGet();
                    return null;
                })
                .when(storage)
                .put(anyString(), any(byte[].class), anyString(), any(CacheControl.class));

        Future<FeedPublication> first = executor.submit(() -> publisher.publish("demo"));
        assertThat(firstWriteStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        Future<FeedPublication> second = executor.submit(() -> publisher.publish("demo"));
        awaitBlocked(second);
        releaseFirstWrite.countDown();

        first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(highestConcurrency).hasValue(1);
    }

    @Test
    void doesNotMakeAPodcastWaitForTheFeedOfAnother() throws Exception {
        CountDownLatch firstWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
                    firstWriteStarted.countDown();
                    releaseFirstWrite.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return null;
                })
                .when(storage)
                .put(eq("slow.xml"), any(byte[].class), anyString(), any(CacheControl.class));

        Future<FeedPublication> slow = executor.submit(() -> publisher.publish("slow"));
        assertThat(firstWriteStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        Future<FeedPublication> other = executor.submit(() -> publisher.publish("other"));

        assertThat(other.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).episodeCount()).isEqualTo(3);
        releaseFirstWrite.countDown();
        slow.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void reportsAStorageFailureAndLetsTheNextWriteThrough() {
        doThrow(new ObjectStorageException("down", new RuntimeException("cause")))
                .doNothing()
                .when(storage)
                .put(anyString(), any(byte[].class), anyString(), any(CacheControl.class));

        assertThatThrownBy(() -> publisher.publish("demo"))
                .isInstanceOf(FeedPublicationException.class)
                .hasMessageContaining("demo")
                .hasCauseInstanceOf(ObjectStorageException.class);

        assertThat(publisher.publish("demo").episodeCount()).isEqualTo(3);
    }

    /** The task cannot finish while it waits for the lock held by the first write. */
    private static void awaitBlocked(Future<?> task) throws InterruptedException {
        Thread.sleep(200);
        assertThat(task.isDone()).isFalse();
    }
}
