package io.github.bergamof.bergacast.feed;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Renders a podcast feed as RSS 2.0 with the Apple Podcasts ({@code itunes:}) and Atom extensions. */
@Component
public class RssFeedWriter {

    static final String ITUNES_NAMESPACE = "http://www.itunes.com/dtds/podcast-1.0.dtd";
    static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";
    private static final String ITUNES = "itunes";
    private static final String ATOM = "atom";
    private static final String RSS_VERSION = "2.0";
    private static final String ENCODING = StandardCharsets.UTF_8.name();
    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    private final XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();

    /** Returns the UTF-8 encoded feed document. */
    public byte[] write(FeedChannel channel, List<FeedItem> items) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            XMLStreamWriter xml = outputFactory.createXMLStreamWriter(output, ENCODING);
            xml.writeStartDocument(ENCODING, "1.0");
            xml.writeStartElement("rss");
            xml.writeAttribute("version", RSS_VERSION);
            xml.writeNamespace(ITUNES, ITUNES_NAMESPACE);
            xml.writeNamespace(ATOM, ATOM_NAMESPACE);
            xml.writeStartElement("channel");
            writeChannel(xml, channel);
            for (FeedItem item : items) {
                writeItem(xml, item);
            }
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException exception) {
            throw new IllegalStateException("Cannot render the feed", exception);
        }
        return output.toByteArray();
    }

    private static void writeChannel(XMLStreamWriter xml, FeedChannel channel) throws XMLStreamException {
        element(xml, "title", channel.title());
        element(xml, "link", channel.link().toString());
        element(xml, "description", channel.description());
        element(xml, "language", channel.language());
        element(xml, "lastBuildDate", RFC_1123.format(channel.lastBuildDate()));
        xml.writeEmptyElement(ATOM, "link", ATOM_NAMESPACE);
        xml.writeAttribute("href", channel.selfUrl().toString());
        xml.writeAttribute("rel", "self");
        xml.writeAttribute("type", "application/rss+xml");
        itunesElement(xml, "author", channel.author());
        if (channel.ownerName() != null || channel.ownerEmail() != null) {
            xml.writeStartElement(ITUNES, "owner", ITUNES_NAMESPACE);
            itunesElement(xml, "name", channel.ownerName());
            itunesElement(xml, "email", channel.ownerEmail());
            xml.writeEndElement();
        }
        if (channel.imageUrl() != null) {
            xml.writeEmptyElement(ITUNES, "image", ITUNES_NAMESPACE);
            xml.writeAttribute("href", channel.imageUrl());
        }
        if (channel.category() != null) {
            xml.writeEmptyElement(ITUNES, "category", ITUNES_NAMESPACE);
            xml.writeAttribute("text", channel.category());
        }
        itunesElement(xml, "explicit", Boolean.toString(channel.explicit()));
    }

    private static void writeItem(XMLStreamWriter xml, FeedItem item) throws XMLStreamException {
        xml.writeStartElement("item");
        element(xml, "title", item.title());
        if (item.description() != null) {
            element(xml, "description", item.description());
        }
        xml.writeStartElement("guid");
        xml.writeAttribute("isPermaLink", "false");
        xml.writeCharacters(item.guid());
        xml.writeEndElement();
        element(xml, "pubDate", RFC_1123.format(item.publicationDate()));
        xml.writeEmptyElement("enclosure");
        xml.writeAttribute("url", item.enclosure().url().toString());
        xml.writeAttribute("length", Long.toString(item.enclosure().lengthBytes()));
        xml.writeAttribute("type", item.enclosure().mimeType());
        itunesElement(xml, "duration", toText(item.durationSeconds()));
        itunesElement(xml, "season", toText(item.season()));
        itunesElement(xml, "episode", toText(item.episodeNumber()));
        itunesElement(xml, "episodeType", item.episodeType().itunesValue());
        itunesElement(xml, "explicit", toText(item.explicit()));
        xml.writeEndElement();
    }

    private static void element(XMLStreamWriter xml, String name, String text) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(text);
        xml.writeEndElement();
    }

    private static void itunesElement(XMLStreamWriter xml, String name, @Nullable String text)
            throws XMLStreamException {
        if (text == null) {
            return;
        }
        xml.writeStartElement(ITUNES, name, ITUNES_NAMESPACE);
        xml.writeCharacters(text);
        xml.writeEndElement();
    }

    private static @Nullable String toText(@Nullable Object value) {
        return value == null ? null : value.toString();
    }
}
