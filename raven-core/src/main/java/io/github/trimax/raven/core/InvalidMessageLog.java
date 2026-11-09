package io.github.trimax.raven.core;

import java.util.List;

import io.github.trimax.raven.core.validation.Violation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Logs the invalid messages received on one session, shared by the server and the client. The first
 * {@value #WARN_LIMIT} are logged at WARN and the rest at DEBUG, so a peer that keeps sending invalid messages
 * cannot flood the log; {@link #summarize()} reports at the end of the session how many were kept out of WARN.
 *
 * <p>Not thread-safe: used only by the thread of its session.
 */
@Slf4j
@RequiredArgsConstructor
final class InvalidMessageLog {

    /** Invalid messages per session logged at WARN. */
    static final int WARN_LIMIT = 10;

    /** Names the peer in the log, e.g. the client ID. */
    private final String peer;

    private int count;

    /**
     * Logs one invalid message and its violations.
     */
    void log(final Message message, final List<Violation> violations) {
        count++;
        if (count <= WARN_LIMIT)
            log.warn("Received invalid {} from {}: {}", message.getClass().getSimpleName(), peer, violations);
        else
            log.debug("Received invalid {} from {}: {}", message.getClass().getSimpleName(), peer, violations);
    }

    /**
     * Called once the session has ended; logs one line if any invalid message was kept out of WARN.
     */
    void summarize() {
        final var skipped = skipped();
        if (skipped > 0)
            log.warn("Skipped {} further invalid messages from {} (logged at DEBUG only)", skipped, peer);
    }

    /**
     * Returns the number of invalid messages logged at DEBUG instead of WARN.
     */
    int skipped() {
        return Math.max(0, count - WARN_LIMIT);
    }
}
