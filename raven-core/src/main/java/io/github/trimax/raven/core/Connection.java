package io.github.trimax.raven.core;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.trimax.raven.core.filter.MessageInputFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * Encapsulates a TCP socket connection with object stream I/O.
 * Outgoing messages are queued and written by a dedicated writer thread, so a slow peer can never block
 * the sender. Provides thread-safe send, blocking receive, and clean disconnect.
 */
@Slf4j
final class Connection {

    /** Maximum number of messages waiting to be written. A peer that falls this far behind is disconnected. */
    static final int SEND_QUEUE_CAPACITY = 1024;

    private final Socket socket;
    private final AtomicBoolean connected;
    private final ObjectInputStream inputStream;
    private final ObjectOutputStream outputStream;
    private final BlockingQueue<Message> outbox = new ArrayBlockingQueue<>(SEND_QUEUE_CAPACITY);
    private final Thread writer;

    Connection(final Socket socket) throws IOException {
        this.socket = socket;
        this.connected = new AtomicBoolean(true);

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
     * Queues a message for sending. Never blocks on the network.
     * If the queue is full, the peer is not keeping up and the connection is closed.
     */
    void send(final Message message) {
        if (!connected.get())
            return;

        if (!outbox.offer(message)) {
            log.warn("Send queue full ({} messages), disconnecting slow peer", SEND_QUEUE_CAPACITY);
            disconnect();
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
            log.warn("Failed to send message: {}", ex.getMessage());
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
