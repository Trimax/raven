package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Transmitter}, on in-memory streams.
 */
class TransmitterTest {

    private final ExecutorService threads = TransportThreads.newExecutor();
    private final AtomicInteger failures = new AtomicInteger();

    @AfterEach
    void closeThreads() {
        // Waits for the writer threads, so a test that does not stop its transmitter hangs here
        threads.close();
    }

    @Test
    void writesFramesInOfferOrder() throws IOException {
        final var bytes = new ByteArrayOutputStream();
        final var transmitter = new Transmitter(1024, Duration.ofSeconds(5), failures::incrementAndGet);
        transmitter.start(new DataOutputStream(bytes), threads);

        final int count = 100;
        for (int i = 0; i < count; i++)
            assertTrue(transmitter.offer(new byte[]{(byte) i}));

        await().atMost(2, TimeUnit.SECONDS).until(() -> bytes.size() == count * 5);
        transmitter.stop();

        final var input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        for (int i = 0; i < count; i++)
            assertArrayEquals(new byte[]{(byte) i}, Frames.readFrame(input, 1));
        assertEquals(0, failures.get());
    }

    @Test
    void offerReturnsFalseWhenQueueStaysFull() {
        final var output = new BlockingOutputStream();
        final var transmitter = new Transmitter(1, Duration.ofMillis(100), failures::incrementAndGet);
        transmitter.start(new DataOutputStream(output), threads);

        // The first frame blocks the writer mid-write, the second fills the queue
        assertTrue(transmitter.offer(new byte[]{1}));
        await().atMost(5, TimeUnit.SECONDS).until(() -> output.entered.getCount() == 0);
        assertTrue(transmitter.offer(new byte[]{2}));

        assertFalse(transmitter.offer(new byte[]{3}), "offer must report a queue that stays full");

        transmitter.stop();
    }

    @Test
    void stopReleasesSendersWaitingForSpace() throws Exception {
        final var output = new BlockingOutputStream();
        final var transmitter = new Transmitter(1, Duration.ofSeconds(30), failures::incrementAndGet);
        transmitter.start(new DataOutputStream(output), threads);
        transmitter.offer(new byte[]{1});
        await().atMost(5, TimeUnit.SECONDS).until(() -> output.entered.getCount() == 0);
        transmitter.offer(new byte[]{2});

        final var waiting = CompletableFuture.supplyAsync(() -> transmitter.offer(new byte[]{3}));
        final var alsoWaiting = CompletableFuture.supplyAsync(() -> transmitter.offer(new byte[]{4}));
        Thread.sleep(100);
        transmitter.stop();

        // Released long before the 30 s send timeout, and not reported as a stalled peer
        assertTrue(waiting.get(2, TimeUnit.SECONDS));
        assertTrue(alsoWaiting.get(2, TimeUnit.SECONDS));
    }

    @Test
    void stopBeforeStartLeavesNoWriterBehind() {
        final var transmitter = new Transmitter(1, Duration.ofSeconds(1), failures::incrementAndGet);
        transmitter.stop();
        transmitter.start(new DataOutputStream(new ByteArrayOutputStream()), threads);
        // closeThreads() would hang if the writer kept waiting for frames
    }

    @Test
    void writeFailureCallsOnFailure() {
        final var transmitter = new Transmitter(16, Duration.ofSeconds(1), failures::incrementAndGet);
        transmitter.start(new DataOutputStream(new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
                throw new IOException("broken pipe");
            }
        }), threads);

        transmitter.offer(new byte[]{1});

        await().atMost(2, TimeUnit.SECONDS).until(() -> failures.get() == 1);
    }

    /** Blocks the first write until the transmitter is stopped, which interrupts the writer. */
    private static final class BlockingOutputStream extends OutputStream {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch never = new CountDownLatch(1);

        @Override
        public void write(final int b) throws IOException {
            entered.countDown();
            try {
                never.await();
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
        }
    }
}
