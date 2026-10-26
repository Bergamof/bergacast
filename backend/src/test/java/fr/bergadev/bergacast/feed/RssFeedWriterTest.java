package fr.bergadev.bergacast.feed;

import static org.assertj.core.api.Assertions.assertThat;

import fr.bergadev.bergacast.episode.EpisodeType;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

class RssFeedWriterTest {

    private static final FeedChannel CHANNEL = new FeedChannel(
            "Tech & Co <weekly>",
            URI.create("https://example.test/site"),
            "All about tech",
            "fr",
            Instant.parse("2026-10-20T08:30:00Z"),
            "Jane Doe",
            "Jane",
            "jane@example.test",
            "https://example.test/cover.png",
            "Technology",
            true,
            URI.create("https://podcasts.example.test/tech.xml"));

    private static final FeedItem FULL_ITEM = new FeedItem(
            UUID.randomUUID(),
            "guid-1",
            "Episode 1",
            "Show notes with <b>HTML</b>",
            Instant.parse("2026-10-19T07:05:09Z"),
            Instant.parse("2026-10-18T07:00:00Z"),
            3600,
            2,
            12,
            EpisodeType.BONUS,
            false,
            new FeedItem.Enclosure(
                    URI.create("https://podcasts.example.test/tech/episodes/1/audio.mp3"), 123, "audio/mpeg"));

    private static final FeedItem MINIMAL_ITEM = new FeedItem(
            UUID.randomUUID(),
            "guid-2",
            "Episode 2",
            null,
            Instant.parse("2026-10-20T07:00:00Z"),
            Instant.parse("2026-10-20T07:00:00Z"),
            null,
            null,
            null,
            EpisodeType.FULL,
            null,
            new FeedItem.Enclosure(
                    URI.create("https://podcasts.example.test/tech/episodes/2/audio.m4a"), 456, "audio/mp4"));

    private XPath xpath;
    private Document document;

    @BeforeEach
    void render() throws Exception {
        byte[] xml = new RssFeedWriter().write(CHANNEL, List.of(FULL_ITEM, MINIMAL_ITEM));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new FeedNamespaces());
        assertThat(new String(xml, StandardCharsets.UTF_8)).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    }

    @Test
    void rendersTheChannel() throws Exception {
        assertThat(text("/rss/@version")).isEqualTo("2.0");
        assertThat(text("/rss/channel/title")).isEqualTo("Tech & Co <weekly>");
        assertThat(text("/rss/channel/link")).isEqualTo("https://example.test/site");
        assertThat(text("/rss/channel/description")).isEqualTo("All about tech");
        assertThat(text("/rss/channel/language")).isEqualTo("fr");
        assertThat(text("/rss/channel/lastBuildDate")).isEqualTo("Tue, 20 Oct 2026 08:30:00 GMT");
        assertThat(text("/rss/channel/atom:link/@href")).isEqualTo("https://podcasts.example.test/tech.xml");
        assertThat(text("/rss/channel/atom:link/@rel")).isEqualTo("self");
        assertThat(text("/rss/channel/itunes:author")).isEqualTo("Jane Doe");
        assertThat(text("/rss/channel/itunes:owner/itunes:name")).isEqualTo("Jane");
        assertThat(text("/rss/channel/itunes:owner/itunes:email")).isEqualTo("jane@example.test");
        assertThat(text("/rss/channel/itunes:image/@href")).isEqualTo("https://example.test/cover.png");
        assertThat(text("/rss/channel/itunes:category/@text")).isEqualTo("Technology");
        assertThat(text("/rss/channel/itunes:explicit")).isEqualTo("true");
    }

    @Test
    void asksAppleAndGoogleNotToListThePodcastInTheirDirectories() throws Exception {
        assertThat(text("/rss/channel/itunes:block")).isEqualTo("yes");
        assertThat(text("/rss/channel/googleplay:block")).isEqualTo("yes");
    }

    @Test
    void rendersItemsInTheGivenOrder() throws Exception {
        assertThat(text("/rss/channel/item[1]/guid")).isEqualTo("guid-1");
        assertThat(text("/rss/channel/item[2]/guid")).isEqualTo("guid-2");
    }

    @Test
    void rendersAllItemFields() throws Exception {
        String item = "/rss/channel/item[1]/";
        assertThat(text(item + "title")).isEqualTo("Episode 1");
        assertThat(text(item + "description")).isEqualTo("Show notes with <b>HTML</b>");
        assertThat(text(item + "guid/@isPermaLink")).isEqualTo("false");
        assertThat(text(item + "pubDate")).isEqualTo("Mon, 19 Oct 2026 07:05:09 GMT");
        assertThat(text(item + "enclosure/@url")).isEqualTo("https://podcasts.example.test/tech/episodes/1/audio.mp3");
        assertThat(text(item + "enclosure/@length")).isEqualTo("123");
        assertThat(text(item + "enclosure/@type")).isEqualTo("audio/mpeg");
        assertThat(text(item + "itunes:duration")).isEqualTo("3600");
        assertThat(text(item + "itunes:season")).isEqualTo("2");
        assertThat(text(item + "itunes:episode")).isEqualTo("12");
        assertThat(text(item + "itunes:episodeType")).isEqualTo("bonus");
        assertThat(text(item + "itunes:explicit")).isEqualTo("false");
    }

    @Test
    void omitsMissingOptionalItemFields() throws Exception {
        String item = "/rss/channel/item[2]/";
        assertThat(count(item + "description")).isZero();
        assertThat(count(item + "itunes:duration")).isZero();
        assertThat(count(item + "itunes:season")).isZero();
        assertThat(count(item + "itunes:episode")).isZero();
        assertThat(count(item + "itunes:explicit")).isZero();
        assertThat(text(item + "itunes:episodeType")).isEqualTo("full");
    }

    @Test
    void omitsMissingOptionalChannelFields() throws Exception {
        FeedChannel minimal = new FeedChannel(
                "T",
                URI.create("https://example.test"),
                "D",
                "en",
                Instant.EPOCH,
                null,
                null,
                null,
                null,
                null,
                false,
                URI.create("https://example.test/t.xml"));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        document = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(new RssFeedWriter().write(minimal, List.of())));

        assertThat(count("/rss/channel/itunes:author")).isZero();
        assertThat(count("/rss/channel/itunes:owner")).isZero();
        assertThat(count("/rss/channel/itunes:image")).isZero();
        assertThat(count("/rss/channel/itunes:category")).isZero();
        assertThat(count("/rss/channel/item")).isZero();
        assertThat(text("/rss/channel/itunes:explicit")).isEqualTo("false");
    }

    @Test
    void stripsCharactersForbiddenByXml() throws Exception {
        FeedChannel channel = new FeedChannel(
                "Bad\u0001title",
                URI.create("https://example.test"),
                "D\uFFFF",
                "en",
                Instant.EPOCH,
                "\u0002",
                null,
                null,
                "https://example.test/i\u0003.png",
                "Cat\u0004",
                false,
                URI.create("https://example.test/t.xml"));
        FeedItem item = new FeedItem(
                UUID.randomUUID(),
                "guid\u0005",
                "Ep\u0006 1",
                "Notes\uD800",
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                null,
                EpisodeType.FULL,
                null,
                new FeedItem.Enclosure(URI.create("https://example.test/a.mp3"), 1, "audio/mpeg"));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        document = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(new RssFeedWriter().write(channel, List.of(item))));

        assertThat(text("/rss/channel/title")).isEqualTo("Badtitle");
        assertThat(text("/rss/channel/description")).isEqualTo("D");
        assertThat(text("/rss/channel/itunes:image/@href")).isEqualTo("https://example.test/i.png");
        assertThat(text("/rss/channel/itunes:category/@text")).isEqualTo("Cat");
        assertThat(text("/rss/channel/item/guid")).isEqualTo("guid");
        assertThat(text("/rss/channel/item/title")).isEqualTo("Ep 1");
        assertThat(text("/rss/channel/item/description")).isEqualTo("Notes");
    }

    private String text(String expression) throws Exception {
        return xpath.evaluate(expression, document);
    }

    private int count(String expression) throws Exception {
        return ((NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET)).getLength();
    }

    private static final class FeedNamespaces implements NamespaceContext {

        @Override
        public String getNamespaceURI(String prefix) {
            return switch (prefix) {
                case "itunes" -> RssFeedWriter.ITUNES_NAMESPACE;
                case "googleplay" -> RssFeedWriter.GOOGLE_PLAY_NAMESPACE;
                case "atom" -> RssFeedWriter.ATOM_NAMESPACE;
                default -> "";
            };
        }

        @Override
        public String getPrefix(String namespaceUri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Iterator<String> getPrefixes(String namespaceUri) {
            throw new UnsupportedOperationException();
        }
    }
}
