package io.github.trimax.raven.core;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.trimax.raven.core.util.HandlingUtil;
import io.github.trimax.raven.core.util.TryUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Encapsulates a TCP socket connection with object stream I/O.
 * Outgoing messages are queued and written by a {@link MessageWriter}, so the sender never waits for the
 * network. A full queue applies bounded back-pressure: send() waits up to the send timeout for space
 * and disconnects the peer if none frees up. Incoming messages are read and filtered by a {@link MessageReader}.
 * Provides thread-safe send, blocking receive, and clean disconnect.
 */
@Slf4j
final class Connection {

    private final Socket socket;
    private final AtomicBoolean connected;
    private final MessageReader reader;
    /** Package-private for tests only: lets them reach the writer thread. */
    final MessageWriter writer;

    /**
     * Sets up the object streams without starting the writer; use {@link #open} unless a test needs that.
     * Does not close the socket on failure.
     *
     * @param queueCapacity maximum number of messages waiting to be written
     * @param sendTimeout   how long send() waits for space in a full queue before disconnecting the peer
     */
    Connection(final Socket socket, final int queueCapacity, final Duration sendTimeout) throws IOException {
        this.socket = socket;
        this.connected = new AtomicBoolean(true);

        // The writer sends our stream header before we wait for the peer's; the other order would leave
        // both sides waiting for each other's header
        this.writer = new MessageWriter(socket.getOutputStream(), queueCapacity, sendTimeout, connected::get, this::disconnect);
        this.reader = new MessageReader(socket.getInputStream());
    }

    /**
     * Performs the stream-header handshake on a connected socket and returns a started connection.
     * The handshake is bounded by {@code handshakeTimeout}; afterward reads block without a timeout.
     *
     * @param handshakeTimeout how long to wait for the peer's stream header
     * @param queueCapacity    maximum number of messages waiting to be written
     * @param sendTimeout      how long send() waits for space in a full queue before disconnecting the peer
     * @throws IOException if the handshake fails or times out; the socket is closed by then
     */
    static Connection open(final Socket socket, final Duration handshakeTimeout,
                           final int queueCapacity, final Duration sendTimeout) throws IOException {
        // The caller never receives a connection if the handshake fails, so the socket is released here
        return HandlingUtil.closeOnFailure(socket, () -> {
            // Bound the stream-header exchange, which otherwise blocks until the peer writes its header
            socket.setSoTimeout((int) handshakeTimeout.toMillis());
            final var connection = new Connection(socket, queueCapacity, sendTimeout);
            socket.setSoTimeout(0);

            // Started last, so a failed handshake never leaves a writer thread behind
            connection.start();
            return connection;
        });
    }

    /**
     * Starts the writer thread. Called once, by {@link #open}.
     */
    void start() {
        writer.start();
    }

    /**
     * Returns whether this connection is still active.
     */
    boolean isConnected() {
        return connected.get();
    }

    /**
     * Queues a message for the writer thread and returns once it is queued; write errors are logged
     * and close the connection instead of being thrown. If the queue is full, waits up to the send timeout
     * for space, then treats the peer as stalled and disconnects it. Messages still queued at
     * {@link #disconnect()} are dropped.
     */
    void send(final Message message) {
        if (connected.get() && !writer.offer(message)) {
            log.warn("Send queue full for {}, disconnecting stalled peer", writer.getSendTimeout());
            disconnect();
        }
    }

    /**
     * Reads the next message from the input stream. Blocks until available.
     *
     * @return the received message, or null if the connection is closed or an error occurs
     */
    Message receive() {
        try {
            return reader.read();
        } catch (final IOException | ClassNotFoundException ex) {
            // Close the socket now: a later disconnect() would see connected == false and skip it
            disconnect();
            return null;
        }
    }

    /**
     * Closes the connection, releasing all resources.
     * Thread-safe: only the first caller actually closes the socket.
     */
    void disconnect() {
        if (!connected.compareAndSet(true, false))
            return;

        writer.stop();
        TryUtil.execute(socket::close);
    }
}
