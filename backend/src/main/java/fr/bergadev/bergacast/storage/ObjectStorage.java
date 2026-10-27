package fr.bergadev.bergacast.storage;

import fr.bergadev.bergacast.config.BergacastProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Writes and deletes the public objects of the bucket. Objects are readable by anyone (bucket policy), with the
 * {@code Content-Type} and {@code Cache-Control} given when writing them.
 */
@Component
public class ObjectStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStorage.class);

    private final S3Client client;
    private final String bucket;

    ObjectStorage(S3Client client, BergacastProperties properties) {
        this.client = client;
        this.bucket = properties.storage().bucket();
    }

    /**
     * Writes an object from content held in memory, replacing any object with the same key.
     *
     * @throws ObjectStorageException when the storage cannot be reached or rejects the request
     */
    public void put(String key, byte[] content, String contentType, CacheControl cacheControl) {
        execute(
                "write",
                key,
                () -> client.putObject(request(key, contentType, cacheControl), RequestBody.fromBytes(content)));
    }

    /**
     * Streams an object of a known size, replacing any object with the same key. The source is opened again if the
     * request has to be retried.
     *
     * @throws ObjectStorageException when the storage cannot be reached or rejects the request
     */
    public void put(
            String key, InputStreamSource source, long sizeBytes, String contentType, CacheControl cacheControl) {
        RequestBody body = RequestBody.fromContentProvider(
                () -> {
                    try {
                        return source.getInputStream();
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                },
                sizeBytes,
                contentType);
        execute("write", key, () -> client.putObject(request(key, contentType, cacheControl), body));
    }

    /**
     * Deletes an object; deleting a missing object succeeds.
     *
     * @throws ObjectStorageException when the storage cannot be reached or rejects the request
     */
    public void delete(String key) {
        execute(
                "delete",
                key,
                () -> client.deleteObject(request -> request.bucket(bucket).key(key)));
    }

    /**
     * Deletes every object whose key starts with {@code prefix}.
     *
     * @throws ObjectStorageException when the storage cannot be reached or rejects the request
     */
    public void deleteAll(String prefix) {
        execute(
                "delete",
                prefix + "*",
                () -> client
                        .listObjectsV2Paginator(
                                request -> request.bucket(bucket).prefix(prefix))
                        .stream()
                        .map(page -> page.contents().stream()
                                .map(S3Object::key)
                                .map(key -> ObjectIdentifier.builder().key(key).build())
                                .toList())
                        .filter(keys -> !keys.isEmpty())
                        .forEach(this::deleteBatch));
    }

    /**
     * Deletes an object that nothing references anymore. A failure is only logged: it leaves an orphan object behind,
     * but the change it follows (already committed) stands.
     */
    public void discard(String key) {
        try {
            delete(key);
        } catch (ObjectStorageException exception) {
            LOGGER.warn("Orphan object left in the storage: {}", key, exception);
        }
    }

    /** Same as {@link #discard} for every object whose key starts with {@code prefix}. */
    public void discardAll(String prefix) {
        try {
            deleteAll(prefix);
        } catch (ObjectStorageException exception) {
            LOGGER.warn("Orphan objects may be left in the storage: {}*", prefix, exception);
        }
    }

    /** One page of a listing holds at most 1000 keys, the most a single delete request accepts. */
    private void deleteBatch(List<ObjectIdentifier> keys) {
        client.deleteObjects(request -> request.bucket(bucket)
                .delete(Delete.builder().objects(keys).quiet(true).build()));
    }

    private PutObjectRequest request(String key, String contentType, CacheControl cacheControl) {
        return PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .cacheControl(cacheControl.getHeaderValue())
                .build();
    }

    private static void execute(String operation, String key, Runnable request) {
        try {
            request.run();
        } catch (SdkException | UncheckedIOException exception) {
            throw new ObjectStorageException("Cannot " + operation + " the object '" + key + "'", exception);
        }
    }
}
