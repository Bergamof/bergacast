package fr.bergadev.bergacast.web;

/**
 * Descriptions of the {@code 503} responses of the operations that write to the object storage. When only the feed
 * could not be written, the change is already committed: the response tells so, and how to write the feed again.
 */
public final class StorageResponses {

    public static final String UNAVAILABLE = "503";

    /** Operations that change what the feed shows, without uploading content. */
    public static final String FEED_NOT_WRITTEN = "The object storage is unavailable: the change is saved but the "
            + "feed could not be written. Do not repeat the change: write the feed again with POST "
            + ApiPaths.PODCAST_FEED_PUBLICATIONS + " once the storage is back.";

    /** Operations that upload content to the storage, then change what the feed shows. */
    public static final String UPLOAD_OR_FEED_FAILED = "The object storage is unavailable. If the upload failed, "
            + "nothing is changed (the detail says so) and the request can be repeated; if only the feed could not "
            + "be written, the change is saved: write the feed again with POST "
            + ApiPaths.PODCAST_FEED_PUBLICATIONS + " once the storage is back.";

    private StorageResponses() {}
}
