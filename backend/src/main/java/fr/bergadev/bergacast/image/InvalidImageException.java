package fr.bergadev.bergacast.image;

/** The submitted bytes are not an acceptable image; the message completes "The image ...". */
public final class InvalidImageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidImageException(String message) {
        super(message);
    }

    InvalidImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
