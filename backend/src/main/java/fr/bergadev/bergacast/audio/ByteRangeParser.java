package fr.bergadev.bergacast.audio;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Parses a single byte range of a {@code Range} header (RFC 9110, section 14.1.2).
 *
 * <p>As allowed by the RFC, a header the server does not support (another unit, several ranges) or cannot parse is
 * ignored, and the whole representation is served.
 */
final class ByteRangeParser {

    private static final Pattern SINGLE_BYTE_RANGE = Pattern.compile("^bytes=(\\d*)-(\\d*)$");
    private static final int FIRST_GROUP = 1;
    private static final int LAST_GROUP = 2;

    private ByteRangeParser() {}

    static ByteRange parse(@Nullable String rangeHeader, long completeLength) {
        ByteRange.Full full = new ByteRange.Full(completeLength);
        if (rangeHeader == null) {
            return full;
        }
        Matcher matcher = SINGLE_BYTE_RANGE.matcher(rangeHeader.strip());
        if (!matcher.matches()) {
            return full;
        }
        String first = matcher.group(FIRST_GROUP);
        String last = matcher.group(LAST_GROUP);
        try {
            if (first.isEmpty()) {
                return last.isEmpty() ? full : suffixRange(Long.parseLong(last), completeLength);
            }
            return boundedRange(Long.parseLong(first), last.isEmpty() ? null : Long.parseLong(last), completeLength);
        } catch (NumberFormatException tooLarge) {
            return full;
        }
    }

    private static ByteRange suffixRange(long suffixLength, long completeLength) {
        if (suffixLength == 0 || completeLength == 0) {
            return new ByteRange.Unsatisfiable(completeLength);
        }
        long first = Math.max(0, completeLength - suffixLength);
        return new ByteRange.Partial(first, completeLength - 1, completeLength);
    }

    private static ByteRange boundedRange(long first, @Nullable Long last, long completeLength) {
        if (last != null && last < first) {
            return new ByteRange.Full(completeLength);
        }
        if (first >= completeLength) {
            return new ByteRange.Unsatisfiable(completeLength);
        }
        long lastInRepresentation = last == null ? completeLength - 1 : Math.min(last, completeLength - 1);
        return new ByteRange.Partial(first, lastInRepresentation, completeLength);
    }
}
