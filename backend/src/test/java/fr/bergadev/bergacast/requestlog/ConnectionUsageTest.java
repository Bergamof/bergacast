package fr.bergadev.bergacast.requestlog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectionUsageTest {

    @Test
    void reportsNoConnectionWhenNoneWasBorrowed() {
        ConnectionUsage.startTracking();

        assertThat(ConnectionUsage.stopTracking()).isFalse();
    }

    @Test
    void reportsABorrowedConnection() {
        ConnectionUsage.startTracking();
        ConnectionUsage.recordConnectionBorrowed();

        assertThat(ConnectionUsage.stopTracking()).isTrue();
    }

    @Test
    void ignoresConnectionsBorrowedOutsideOfATracking() {
        ConnectionUsage.recordConnectionBorrowed();
        ConnectionUsage.startTracking();

        assertThat(ConnectionUsage.stopTracking()).isFalse();
    }
}
