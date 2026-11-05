package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PendingConnect}.
 */
class PendingConnectTest {

    private final PendingConnect pending = new PendingConnect();

    @Test
    void beginReturnsNullWhileAttemptInProgress() {
        final var attempt = pending.begin();

        assertNotNull(attempt);
        assertFalse(attempt.socket().isConnected());
        assertNull(pending.begin(), "a second attempt must not begin while one is in progress");
    }

    @Test
    void finishReleasesAttemptAndReportsItCurrent() {
        final var attempt = pending.begin();

        assertTrue(pending.finish(attempt));
        assertFalse(attempt.socket().isClosed(), "finish must leave the socket to its connection");
        assertNotNull(pending.begin(), "a new attempt may begin once the previous one finished");
    }

    @Test
    void cancelClosesSocketAndMarksAttemptCancelled() {
        final var attempt = pending.begin();

        pending.cancel();

        assertTrue(attempt.socket().isClosed(), "cancel must unblock the connecting thread");
        assertFalse(pending.finish(attempt));
        assertNotNull(pending.begin(), "a new attempt may begin once the previous one was cancelled");
    }

    @Test
    void cancelWithoutAttemptCancelsNothingLater() {
        pending.cancel();

        final var attempt = pending.begin();
        assertTrue(pending.finish(attempt), "an attempt begun after cancel() is not cancelled by it");
    }

    @Test
    void finishOfCancelledAttemptKeepsNewerAttempt() {
        final var cancelled = pending.begin();
        pending.cancel();
        final var current = pending.begin();

        assertFalse(pending.finish(cancelled));
        assertNull(pending.begin(), "the newer attempt must still be in progress");
        assertFalse(current.socket().isClosed());
        assertTrue(pending.finish(current));
    }

    @Test
    void finishTwiceIsSafe() {
        final var attempt = pending.begin();

        assertTrue(pending.finish(attempt));
        assertTrue(pending.finish(attempt));
        assertNotNull(pending.begin());
    }
}
