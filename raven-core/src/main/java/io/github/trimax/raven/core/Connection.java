package io.github.trimax.raven.core;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.trimax.raven.core.filter.MessageInputFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * Encapsulates a TCP socket connection with object stream I/O.
 * Outgoing messages are queued and written by a dedicated writer thread, so the sender never waits for the
 * network. A full queue applies bounded back-pressure: send() waits up to the send timeout for space
 * and disconnects the peer if none frees up. Provides thread-safe send, blocking receive, and clean disconnect.
 */
@Slf4j
final class Connection {

    private final Socket socket;
    private final AtomicBoolean connected;
    private final ObjectInputStream inputStream;
    private final ObjectOutputStream outputStream;
    private final BlockingQueue<Message> outbox;
    private final Duration sendTimeout;
    /** Package-private for tests only: lets them verify the writer terminates. */
    final Thread writer;

    /**
     * @param queueCapacity maximum number of messages waiting to be written
     * @param sendTimeout   how long send() waits for space in a full queue before disconnecting the peer
     */
    Connection(final Socket socket, final int queueCapacity, final Duration sendTimeout) throws IOException {
        this.socket = socket;
        this.connected = new AtomicBoolean(true);
        this.outbox = new ArrayBlockingQueue<>(queueCapacity);
        this.sendTimeout = sendTimeout;

        try {
            this.outputStream = new ObjectOutputStream(socket.getOutputStream());
            this.inputStream = new ObjectInputStream(socket.getInputStream());
        } catch (final IOException | RuntimeException exception) {
            close(socket);
            throw exception;
        }

        this.inputStream.setObjectInputFilter(MessageInputFilter.INSTANCE);
        this.writer = Thread.ofVirtual().name("raven-writer").unstarted(this::writeLoop);
        this.writer.start();
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
        if (!connected.get())
            return;

        try {
            if (!outbox.offer(message, sendTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Send queue full for {}, disconnecting stalled peer", sendTimeout);
                disconnect();
            }
        } catch (final InterruptedException ex) {
            log.debug("Interrupted while queueing message, dropping it");
            Thread.currentThread().interrupt();
        }
    }

    private void writeLoop() {
        try {
            while (connected.get()) {
                final var message = outbox.take();
                outputStream.writeObject(message);
                outputStream.flush();
                outputStream.reset();
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (final IOException ex) {
            // disconnect() closes the socket under an in-flight write: that is ordinary shutdown, not a failure
            if (connected.get())
                log.warn("Failed to send message: {}", ex.getMessage());
            else
                log.debug("Writer stopped by disconnect: {}", ex.getMessage());
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
            final var object = inputStream.readObject();
            if (object instanceof Message message)
                return message;

            log.warn("Received non-RavenMessage object: {}", object.getClass().getName());
            return null;
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

        writer.interrupt();
        close(this.socket);
    }

    private static void close(final Socket socket) {
        try {
            socket.close();
        } catch (final IOException ex) {
            log.debug("Error closing socket: {}", ex.getMessage());
        }
    }
}
