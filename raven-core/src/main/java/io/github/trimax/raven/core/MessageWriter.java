package io.github.trimax.raven.core;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes the outgoing messages of one {@link Connection} on a dedicated virtual thread, in the order they were
 * offered, so the sender never waits for the network. The queue is bounded: {@link #offer(Message)} waits up to
 * the send timeout for space and reports a peer that stays stalled.
 */
@Slf4j
final class MessageWriter {

    private final ObjectOutputStream outputStream;
    private final BlockingQueue<Message> outbox;
    @Getter
    private final Duration sendTimeout;

    /** Whether the owning connection is still open; the loop runs only while it is. */
    private final BooleanSupplier connected;

    /** Invoked once if a write fails, so the owning connection can close. */
    private final Runnable onFailure;

    /** Package-private for tests only: lets them verify the writer terminates. */
    final Thread thread;

    /**
     * Wraps the stream in an {@link ObjectOutputStream}, which writes the stream header to the peer at once.
     *
     * @param output        the raw output stream of the connection
     * @param queueCapacity maximum number of messages waiting to be written
     * @param sendTimeout   how long {@link #offer(Message)} waits for space in a full queue
     * @throws IOException if the stream header cannot be written
     */
    MessageWriter(final OutputStream output, final int queueCapacity, final Duration sendTimeout,
                  final BooleanSupplier connected, final Runnable onFailure) throws IOException {
        this.outputStream = new ObjectOutputStream(output);
        this.outbox = new ArrayBlockingQueue<>(queueCapacity);
        this.sendTimeout = sendTimeout;
        this.connected = connected;
        this.onFailure = onFailure;
        this.thread = Thread.ofVirtual().name("raven-writer").unstarted(this::writeLoop);
    }

    /**
     * Starts the writer thread. Called once.
     */
    void start() {
        thread.start();
    }

    /**
     * Stops the writer thread. Messages still queued are dropped, which also wakes senders waiting for space.
     */
    void stop() {
        thread.interrupt();
        outbox.clear();
    }

    /**
     * Queues a message, waiting up to the send timeout for space if the queue is full.
     * If the calling thread is interrupted while waiting, the message is dropped and the interrupt is restored;
     * that is not a stalled peer, so it is reported as success.
     *
     * @return {@code false} if the queue stayed full for the whole send timeout
     */
    boolean offer(final Message message) {
        try {
            final var queued = outbox.offer(message, sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            // A sender woken by stop() takes one freed slot; clearing again wakes the next waiting sender too
            if (queued && !connected.getAsBoolean())
                outbox.clear();
            return queued;
        } catch (final InterruptedException ex) {
            log.debug("Interrupted while queueing message, dropping it");
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private void writeLoop() {
        try {
            while (connected.getAsBoolean()) {
                final var message = outbox.take();
                outputStream.writeObject(message);
                outputStream.flush();
                outputStream.reset();
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (final IOException ex) {
            // disconnect() closes the socket under an in-flight write: that is ordinary shutdown, not a failure
            if (connected.getAsBoolean())
                log.warn("Failed to send message: {}", ex.getMessage());
            else
                log.debug("Writer stopped by disconnect: {}", ex.getMessage());
            onFailure.run();
        } catch (final RuntimeException ex) {
            // E.g. a message's own writeObject() threw; the stream may hold a partial object, so it cannot be reused
            log.warn("Failed to send message: {}", ex.toString(), ex);
            onFailure.run();
        } catch (final Error error) {
            // Not handled here, but the connection must not stay open without a writer
            onFailure.run();
            throw error;
        }
    }
}
