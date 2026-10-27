package fr.bergadev.bergacast.storage;

/** The object storage could not be reached, or it rejected a request. */
public class ObjectStorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
