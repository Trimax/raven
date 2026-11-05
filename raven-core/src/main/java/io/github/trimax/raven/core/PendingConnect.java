package io.github.trimax.raven.core;

import java.net.Socket;

import io.github.trimax.raven.core.util.TryUtil;

/**
 * The connection attempt of a {@link RavenClient} that is in progress, if any. Lets
 * {@link RavenClient#disconnect()} abort an attempt whose network I/O runs outside the client's monitor.
 *
 * <p>Not thread-safe: every method must be called while holding the owning client's monitor.
 */
final class PendingConnect {

    /**
     * One connection attempt: the socket it connects and the generation it started in.
     */
    record Attempt(Socket socket, long generation) {
    }

    /** Socket of the connection attempt in progress, or {@code null}. */
    private Socket socket;

    /** Incremented by {@link #cancel()} so that an in-flight attempt discards its result. */
    private long generation;

    /**
     * Starts a new attempt with a fresh, unconnected socket.
     *
     * @return the new attempt, or {@code null} if another attempt is in progress
     */
    Attempt begin() {
        if (socket != null)
            return null;

        socket = new Socket();
        return new Attempt(socket, generation);
    }

    /**
     * Cancels the attempt in progress, if any; every attempt begun before this call will report itself
     * cancelled from {@link #finish(Attempt)}.
     */
    void cancel() {
        generation++;
        if (socket == null)
            return;

        // Unblocks the connecting thread instead of letting it wait for the connect timeout
        TryUtil.execute(socket::close);
        socket = null;
    }

    /**
     * Ends an attempt, so that a new one can begin. Safe to call more than once for the same attempt.
     *
     * @return {@code false} if the attempt was cancelled and its result must be discarded
     */
    boolean finish(final Attempt attempt) {
        // cancel() may have cleared the field already and a newer attempt may own it now
        if (socket == attempt.socket())
            socket = null;

        return generation == attempt.generation();
    }
}
