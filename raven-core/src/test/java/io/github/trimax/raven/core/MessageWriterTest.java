package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MessageWriter}.
 */
class MessageWriterTest {

    @Test
    void writesMessagesInOfferOrder() throws Exception {
        try (final var serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
             final var socket = new Socket("localhost", serverSocket.getLocalPort());
             final var peer = serverSocket.accept()) {
            final var writer = new MessageWriter(new ObjectOutputStream(socket.getOutputStream()),
                    TestConnections.QUEUE_CAPACITY, TestConnections.SEND_TIMEOUT, () -> true, () -> { });
            final var input = new ObjectInputStream(peer.getInputStream());
            writer.start();

            final int count = 100;
            for (int i = 0; i < count; i++)
                assertTrue(writer.offer(new TestMessage(String.valueOf(i))));

            for (int i = 0; i < count; i++)
                assertEquals(String.valueOf(i), ((TestMessage) input.readObject()).getContent());

            writer.stop();
        }
    }

    @Test
    void offerReturnsFalseWhenQueueStaysFull() throws Exception {
        final var output = new BlockingOutputStream();
        final var writer = new MessageWriter(new ObjectOutputStream(output), 1, Duration.ofMillis(100), () -> true, () -> { });
        output.block();
        writer.start();

        // The first message blocks the writer thread mid-write, the second fills the queue
        assertTrue(writer.offer(new TestMessage("in flight")));
        await().atMost(5, TimeUnit.SECONDS).until(output::isBlocked);
        assertTrue(writer.offer(new TestMessage("queued")));

        assertFalse(writer.offer(new TestMessage("rejected")), "offer must report a queue that stays full");

        writer.stop();
        output.release();
    }

    @Test
    void stopEndsThread() throws Exception {
        final var writer = new MessageWriter(new ObjectOutputStream(new ByteArrayOutputStream()),
                TestConnections.QUEUE_CAPACITY, TestConnections.SEND_TIMEOUT, () -> true, () -> { });
        writer.start();

        writer.stop();

        assertTrue(writer.thread.join(Duration.ofSeconds(5)), "writer thread must terminate once stopped");
    }

    @Test
    void writeFailureCallsOnFailure() throws Exception {
        final var output = new FailingOutputStream();
        final var failures = new AtomicInteger();
        final var writer = new MessageWriter(new ObjectOutputStream(output),
                TestConnections.QUEUE_CAPACITY, TestConnections.SEND_TIMEOUT, () -> true, failures::incrementAndGet);
        output.fail.set(true);
        writer.start();

        writer.offer(new TestMessage("boom"));

        assertTrue(writer.thread.join(Duration.ofSeconds(5)), "writer thread must end after a write failure");
        assertEquals(1, failures.get());
    }

    /** An output stream whose writes block once {@link #block()} has been called, until {@link #release()}. */
    private static final class BlockingOutputStream extends OutputStream {
        private final AtomicBoolean armed = new AtomicBoolean();
        private final AtomicBoolean blocked = new AtomicBoolean();
        private final CountDownLatch released = new CountDownLatch(1);

        void block() {
            armed.set(true);
        }

        void release() {
            released.countDown();
        }

        boolean isBlocked() {
            return blocked.get();
        }

        @Override
        public void write(final int b) throws IOException {
            if (!armed.get())
                return;

            blocked.set(true);
            try {
                released.await();
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
        }
    }

    /** An output stream whose writes fail once {@link #fail} is set. */
    private static final class FailingOutputStream extends OutputStream {
        private final AtomicBoolean fail = new AtomicBoolean();

        @Override
        public void write(final int b) throws IOException {
            if (fail.get())
                throw new IOException("simulated write failure");
        }
    }
}
