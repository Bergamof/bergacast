package io.github.bergamof.bergacast.feed;

/**
 * Removes the characters XML 1.0 forbids (most C0 controls, U+FFFE, U+FFFF, lone surrogates), which user-provided
 * metadata may contain and which would otherwise make the whole feed unparseable.
 */
final class XmlCharacters {

    private static final int TAB = 0x9;
    private static final int LINE_FEED = 0xA;
    private static final int CARRIAGE_RETURN = 0xD;
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int LAST_BEFORE_SURROGATES = 0xD7FF;
    private static final int FIRST_AFTER_SURROGATES = 0xE000;
    private static final int LAST_OF_BASIC_PLANE = 0xFFFD;
    private static final int FIRST_SUPPLEMENTARY = 0x10000;

    private XmlCharacters() {}

    static String sanitize(String text) {
        if (text.codePoints().allMatch(XmlCharacters::isAllowed)) {
            return text;
        }
        StringBuilder sanitized = new StringBuilder(text.length());
        text.codePoints().filter(XmlCharacters::isAllowed).forEach(sanitized::appendCodePoint);
        return sanitized.toString();
    }

    /** {@code Char} production of XML 1.0 (section 2.2). */
    private static boolean isAllowed(int codePoint) {
        return codePoint == TAB
                || codePoint == LINE_FEED
                || codePoint == CARRIAGE_RETURN
                || (codePoint >= FIRST_PRINTABLE && codePoint <= LAST_BEFORE_SURROGATES)
                || (codePoint >= FIRST_AFTER_SURROGATES && codePoint <= LAST_OF_BASIC_PLANE)
                || (codePoint >= FIRST_SUPPLEMENTARY && codePoint <= Character.MAX_CODE_POINT);
    }
}
