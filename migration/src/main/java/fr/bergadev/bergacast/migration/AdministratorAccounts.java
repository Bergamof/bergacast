package fr.bergadev.bergacast.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The administrators of the database: only the first one is created here, the backend manages the others. */
final class AdministratorAccounts {

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final Clock clock;

    AdministratorAccounts(Clock clock) {
        this.clock = clock;
    }

    boolean anyExists(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM admin_user)")) {
            result.next();
            return result.getBoolean(1);
        }
    }

    /**
     * Creates the administrator unless there is one already, in one statement so that concurrent runs cannot both
     * create one.
     *
     * @return whether the administrator was created
     */
    boolean createIfNone(Connection connection, InitialAdministrator administrator) throws SQLException {
        String sql = """
                INSERT INTO admin_user (id, username, password_hash, created_at)
                SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM admin_user)""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Object[] parameters = {
                UUID.randomUUID(),
                administrator.username(),
                passwordEncoder.encode(administrator.password()),
                Timestamp.from(clock.instant())
            };
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement.executeUpdate() == 1;
        }
    }
}
