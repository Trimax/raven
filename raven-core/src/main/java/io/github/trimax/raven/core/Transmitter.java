package io.github.trimax.raven.core;

import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.github.trimax.raven.core.util.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes the outgoing payloads of one {@link Session} on its own virtual thread, in the order they were offered, so
 * the sender never waits for the network. The queue is bounded: {@link #offer(byte[])} waits up to the send timeout
 * for space and reports a peer that stays stalled.
 */
@Slf4j
final class Transmitter {

    private final BlockingQueue<byte[]> queue;
    private final Duration sendTimeout;

    /** Invoked if a write fails, so the owning session can close. */
    private final Runnable onFailure;

    private volatile boolean stopped;

    /** The writer thread once it runs, so {@link #stop()} can interrupt it. */
    private volatile Thread writer;

    /**
     * @param capacity    maximum number of payloads waiting to be written
     * @param sendTimeout how long {@link #offer(byte[])} waits for space in a full queue
     */
    Transmitter(final int capacity, final Duration sendTimeout, final Runnable onFailure) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.sendTimeout = sendTimeout;
        this.onFailure = onFailure;
    }

    /**
     * Starts the writer thread. Called once; does nothing visible if {@link #stop()} came first.
     */
    void start(final DataOutputStream output, final ExecutorService threads) {
        ThreadUtil.execute(threads, "raven-writer", () -> writeLoop(output));
    }

    /**
     * Stops the writer thread. WireFormat still queued are dropped, which also wakes senders waiting for space.
     */
    void stop() {
        stopped = true;
        final var thread = writer;
        if (thread != null)
            thread.interrupt();
        queue.clear();
    }

    /**
     * Queues a payload, waiting up to the send timeout for space if the queue is full.
     * If the calling thread is interrupted while waiting, the payload is dropped and the interrupt is restored;
     * that is not a stalled peer, so it is reported as success.
     *
     * @return {@code false} if the queue stayed full for the whole send timeout
     */
    boolean offer(final byte[] payload) {
        try {
            final var queued = queue.offer(payload, sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            // A sender woken by stop() takes one freed slot; clearing again wakes the next waiting sender too
            if (queued && stopped)
                queue.clear();
            return queued;
        } catch (final InterruptedException ex) {
            log.debug("Interrupted while queueing a message, dropping it");
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private void writeLoop(final DataOutputStream output) {
        // Published before the check, so stop() either sees the thread or this loop sees the stop
        writer = Thread.currentThread();
        try {
            while (!stopped) {
                WireFormat.writeFrame(output, queue.take());
                // WireFormat queued meanwhile go out in the same flush
                if (queue.isEmpty())
                    output.flush();
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (final IOException ex) {
            // close() closes the socket under an in-flight write: that is ordinary shutdown, not a failure
            if (stopped)
                log.debug("Writer stopped by close: {}", ex.getMessage());
            else
                log.warn("Failed to send message: {}", ex.getMessage());
            onFailure.run();
        } catch (final RuntimeException | Error ex) {
            // Not expected, but the session must not stay open without a writer
            onFailure.run();
            throw ex;
        }
    }
}
