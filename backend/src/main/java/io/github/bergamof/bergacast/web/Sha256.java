package io.github.bergamof.bergacast.web;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 helpers producing lowercase hexadecimal digests. */
public final class Sha256 {

    private static final String ALGORITHM = "SHA-256";

    private Sha256() {}

    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is mandatory on every Java platform", exception);
        }
    }

    public static String hex(byte[] content) {
        return toHex(newDigest().digest(content));
    }

    public static String hex(MessageDigest digest) {
        return toHex(digest.digest());
    }

    private static String toHex(byte[] digestBytes) {
        return HexFormat.of().formatHex(digestBytes);
    }
}
