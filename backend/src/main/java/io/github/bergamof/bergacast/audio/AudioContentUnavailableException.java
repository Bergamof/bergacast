package io.github.bergamof.bergacast.audio;

/** The large object holding an audio file no longer exists, typically because its episode was just deleted. */
public class AudioContentUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    AudioContentUnavailableException(long oid, Throwable cause) {
        super("Large object " + oid + " does not exist", cause);
    }
}
