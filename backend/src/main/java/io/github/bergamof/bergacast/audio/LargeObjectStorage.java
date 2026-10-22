package io.github.bergamof.bergacast.audio;

import io.github.bergamof.bergacast.web.Sha256;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
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

    /** Writes {@code length} bytes of large object {@code oid}, starting at {@code offset}, to {@code target}. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public void copy(long oid, long offset, long length, OutputStream target) throws IOException {
        try (LargeObject largeObject = largeObjectManager().open(oid, LargeObjectManager.READ)) {
            largeObject.seek64(offset, LargeObject.SEEK_SET);
            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = length;
            while (remaining > 0) {
                int read = largeObject.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read <= 0) {
                    throw new IOException("Large object " + oid + " is shorter than expected");
                }
                target.write(buffer, 0, read);
                remaining -= read;
            }
        } catch (SQLException exception) {
            throw new IOException("Cannot read the large object " + oid, exception);
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
