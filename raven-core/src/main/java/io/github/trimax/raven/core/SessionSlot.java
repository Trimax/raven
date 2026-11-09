package io.github.trimax.raven.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;

/**
 * Holds the one session of a {@link RavenClient}. A new session starts only once the previous one has ended, so the
 * callbacks of two sessions never overlap: {@code onDisconnect} of the old one always comes before {@code onConnect}
 * of the new one.
 *
 * <p>The lock guards only the swap of {@link #current}; no callback and no I/O ever runs under it.
 */
@RequiredArgsConstructor
final class SessionSlot {

    private final ExecutorService threads;

    /**
     * The latest session, possibly waiting to start, connecting or already closed; {@code null} before the first
     * {@link #open}. Written under the lock; volatile because {@link #isOpen()} and {@link #send} read it without.
     */
    private volatile Session current;

    /**
     * Opens a new session made by {@code factory}, unless the current one is open or still connecting.
     *
     * @return completes once the session, new or current, has run {@code onOpen} or ended without it
     */
    synchronized CompletableFuture<Void> open(final Supplier<Session> factory) {
        final var previous = current;
        if (previous != null && !previous.isClosed())
            return previous.opened();

        final var next = factory.get();
        current = next;
        if (previous == null)
            next.start(threads);
        else
            previous.ended().thenRun(() -> next.start(threads));

        return next.opened();
    }

    /**
     * Closes the current session, wherever it is in its life cycle; one that has not started yet ends without callbacks.
     *
     * @return completes once the session has ended
     */
    synchronized CompletableFuture<Void> close() {
        if (current == null)
            return CompletableFuture.completedFuture(null);

        // Closing the socket ends the session; interrupting its thread is not needed and would leave the interrupt
        // flag set inside onDisconnect
        current.close();
        return current.ended();
    }

    boolean isOpen() {
        final var session = current;
        return session != null && session.isOpen();
    }

    /**
     * Queues a payload on the current session; dropped if it is not open.
     */
    void send(final byte[] payload) {
        final var session = current;
        if (session != null)
            session.send(payload);
    }
}
