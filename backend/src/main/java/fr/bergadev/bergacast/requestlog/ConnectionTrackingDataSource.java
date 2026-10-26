package fr.bergadev.bergacast.requestlog;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Records in {@link ConnectionUsage} every connection borrowed from the pool. */
class ConnectionTrackingDataSource extends DelegatingDataSource {

    ConnectionTrackingDataSource(DataSource pool) {
        super(pool);
    }

    @Override
    public Connection getConnection() throws SQLException {
        ConnectionUsage.recordConnectionBorrowed();
        return super.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        ConnectionUsage.recordConnectionBorrowed();
        return super.getConnection(username, password);
    }
}
