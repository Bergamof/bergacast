package io.github.bergamof.bergacast.web;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Builds strong entity tags and evaluates {@code If-Match} preconditions (RFC 9110, section 13.1.1). */
public final class EntityTags {

    private static final String ANY = "*";
    private static final String PART_SEPARATOR = ".";
    private static final String LIST_SEPARATOR = ",";

    private EntityTags() {}

    /** Returns a strong entity tag made of the given parts, e.g. {@code "3.12"}. */
    public static String strong(Object... parts) {
        return Arrays.stream(parts).map(String::valueOf).collect(Collectors.joining(PART_SEPARATOR, "\"", "\""));
    }

    /**
     * Ensures that an optional {@code If-Match} header matches the current entity tag.
     *
     * @throws PreconditionFailedException when the header is present and none of its tags matches (strong comparison)
     */
    public static void requireIfMatch(@Nullable String ifMatchHeader, String currentEntityTag) {
        if (ifMatchHeader == null || ifMatchHeader.isBlank()) {
            return;
        }
        boolean matches = Arrays.stream(ifMatchHeader.split(LIST_SEPARATOR))
                .map(String::trim)
                .anyMatch(tag -> ANY.equals(tag) || currentEntityTag.equals(tag));
        if (!matches) {
            throw new PreconditionFailedException(
                    "The resource has been modified: If-Match does not match the current ETag " + currentEntityTag);
        }
    }
}
