package io.github.bergamof.bergacast.audio;

import io.github.bergamof.bergacast.web.Sha256;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Streams binary content into and out of PostgreSQL large objects, without buffering it entirely in memory.
 *
 * <p>Large object descriptors are only valid inside a transaction, hence the mandatory transaction: the caller's
 * transaction (and JDBC connection) is reused, so a rollback also discards a newly written large object.
 */
@Component
class LargeObjectStorage {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int END_OF_STREAM = -1;
    /** PostgreSQL error code raised when opening a large object that does not exist. */
    private static final String UNDEFINED_OBJECT = "42704";

    private final DataSource dataSource;

    LargeObjectStorage(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Copies {@code content} into a new large object, computing its size and SHA-256 on the fly. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StoredContent store(InputStream content) throws IOException {
        LargeObjectManager manager = largeObjectManager();
        try {
            long oid = manager.createLO(LargeObjectManager.READWRITE);
            MessageDigest digest = Sha256.newDigest();
            long size = 0;
            try (LargeObject largeObject = manager.open(oid, LargeObjectManager.WRITE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = content.read(buffer)) != END_OF_STREAM) {
                    largeObject.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    size += read;
                }
            }
            return new StoredContent(oid, size, Sha256.hex(digest));
        } catch (SQLException exception) {
            throw new IOException("Cannot write the large object", exception);
        }
    }

    /**
     * Reads {@code length} bytes of large object {@code oid} starting at {@code offset} (fewer at its end).
     *
     * @throws AudioContentUnavailableException when the large object does not exist
     */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public byte[] read(long oid, long offset, int length) {
        try (LargeObject largeObject = largeObjectManager().open(oid, LargeObjectManager.READ)) {
            largeObject.seek64(offset, LargeObject.SEEK_SET);
            byte[] buffer = new byte[length];
            int filled = 0;
            while (filled < length) {
                int read = largeObject.read(buffer, filled, length - filled);
                if (read <= 0) {
                    return Arrays.copyOf(buffer, filled);
                }
                filled += read;
            }
            return buffer;
        } catch (SQLException exception) {
            if (UNDEFINED_OBJECT.equals(exception.getSQLState())) {
                throw new AudioContentUnavailableException(oid, exception);
            }
            throw new UncheckedIOException(new IOException("Cannot read the large object " + oid, exception));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private LargeObjectManager largeObjectManager() throws IOException {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return connection.unwrap(PGConnection.class).getLargeObjectAPI();
        } catch (SQLException exception) {
            throw new IOException("Large objects require a PostgreSQL connection", exception);
        }
    }
}
