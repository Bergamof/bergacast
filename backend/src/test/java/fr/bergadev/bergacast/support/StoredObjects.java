package fr.bergadev.bergacast.support;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.test.context.TestComponent;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

/** Reads the objects written to the test bucket, to check what podcast apps would download. */
@TestComponent
public class StoredObjects {

    private final S3Client client;

    StoredObjects(S3Client client) {
        this.client = client;
    }

    /** Returns the object stored with {@code key}, if any. */
    public Optional<StoredObject> find(String key) {
        try {
            ResponseBytes<GetObjectResponse> object = client.getObjectAsBytes(request ->
                    request.bucket(TestcontainersConfiguration.BUCKET).key(key));
            GetObjectResponse response = object.response();
            return Optional.of(new StoredObject(object.asByteArray(), response.contentType(), response.cacheControl()));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        }
    }

    /** Returns the object stored with {@code key}, failing the test when there is none. */
    public StoredObject get(String key) {
        return find(key).orElseThrow(() -> new AssertionError("No object '" + key + "' in the bucket"));
    }

    /** Returns the keys of the objects whose key starts with {@code prefix}, in lexicographic order. */
    public List<String> keys(String prefix) {
        return client
                .listObjectsV2Paginator(request ->
                        request.bucket(TestcontainersConfiguration.BUCKET).prefix(prefix))
                .contents()
                .stream()
                .map(S3Object::key)
                .sorted()
                .toList();
    }

    /** Writes an object, e.g. to check that the application removes it. */
    public void put(String key, byte[] content) {
        client.putObject(
                request -> request.bucket(TestcontainersConfiguration.BUCKET).key(key), RequestBody.fromBytes(content));
    }

    /**
     * An object of the bucket.
     *
     * @param content its bytes
     * @param contentType its {@code Content-Type}
     * @param cacheControl its {@code Cache-Control}
     */
    public record StoredObject(byte[] content, String contentType, String cacheControl) {}
}
