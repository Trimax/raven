package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.util.TryUtil;

/**
 * Unit tests for {@link Acceptor}: the connection limit, the backoff while accept() fails, and the end of the loop
 * with its server socket.
 */
class AcceptorTest {

    private final List<Socket> accepted = new CopyOnWriteArrayList<>();

    @Test
    void loopEndsWhenItsSocketIsClosed() throws Exception {
        final var threads = TransportThreads.newExecutor();
        final var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        new Acceptor(socket, 1, accepted::add).start(threads);

        socket.close();

        threads.shutdown();
        assertTrue(threads.awaitTermination(2, TimeUnit.SECONDS), "accept loop must end once its server socket is closed");
    }

    @Test
    void connectionBeyondTheLimitIsClosedAndReleaseFreesItsSlot() throws Exception {
        final var threads = TransportThreads.newExecutor();
        try (final var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
             final var first = new Socket(InetAddress.getLoopbackAddress(), socket.getLocalPort())) {
            final var acceptor = new Acceptor(socket, 1, accepted::add);
            acceptor.start(threads);
            await().atMost(2, TimeUnit.SECONDS).until(() -> accepted.size() == 1);
            assertEquals(0, acceptor.availablePermits());

            try (final var second = new Socket(InetAddress.getLoopbackAddress(), socket.getLocalPort())) {
                second.setSoTimeout(2_000);
                assertEquals(-1, second.getInputStream().read(), "a connection beyond the limit must be closed");
            }
            assertEquals(1, accepted.size());

            acceptor.release();
            assertEquals(1, acceptor.availablePermits());
            try (final var third = new Socket(InetAddress.getLoopbackAddress(), socket.getLocalPort())) {
                await().atMost(2, TimeUnit.SECONDS).until(() -> accepted.size() == 2);
            }
        } finally {
            accepted.forEach(peer -> TryUtil.execute(peer::close));
            threads.close();
        }
    }

    @Test
    void backsOffWhileAcceptFailsAndRecovers() throws Exception {
        final int failing = 5;
        final var threads = TransportThreads.newExecutor();
        try (final var socket = new FailingServerSocket(failing)) {
            new Acceptor(socket, 16, accepted::add).start(threads);
            // Becomes acceptable once the failures are over: the backlog holds the connection until then
            try (final var ignored = new Socket(InetAddress.getLoopbackAddress(), socket.getLocalPort())) {
                await().atMost(5, TimeUnit.SECONDS).until(() -> accepted.size() == 1);
            }

            // The failures, the successful accept() and possibly the next, still blocked one
            final var attempts = socket.attempts;
            assertTrue(attempts.size() <= failing + 2, "accept() must not be retried without a pause: " + attempts.size());
            // 10, 20, 40, 80 and 160 ms between the attempts; Thread.sleep never wakes early
            for (int i = 1; i <= failing; i++) {
                final long gapMs = TimeUnit.NANOSECONDS.toMillis(attempts.get(i) - attempts.get(i - 1));
                final long expectedMs = 10L << (i - 1);
                assertTrue(gapMs >= expectedMs - 1, "attempt " + i + " came after " + gapMs + "ms, expected " + expectedMs + "ms");
            }
        } finally {
            accepted.forEach(peer -> TryUtil.execute(peer::close));
            threads.close();
        }
    }

    @Test
    void closingDuringBackoffEndsLoopPromptly() throws Exception {
        final var threads = TransportThreads.newExecutor();
        final var socket = new FailingServerSocket(Integer.MAX_VALUE);
        new Acceptor(socket, 1, accepted::add).start(threads);
        // After 8 failures the delay has reached its 1 s cap
        await().atMost(5, TimeUnit.SECONDS).until(() -> socket.attempts.size() >= 8);

        socket.close();
        threads.shutdown();
        assertTrue(threads.awaitTermination(1500, TimeUnit.MILLISECONDS), "accept loop must end within one backoff delay of a close");
    }

    /** A loopback server socket whose first {@code failures} accept() calls fail; records when each call came. */
    private static final class FailingServerSocket extends ServerSocket {
        private final AtomicInteger remainingFailures;
        private final List<Long> attempts = new CopyOnWriteArrayList<>();

        FailingServerSocket(final int failures) throws IOException {
            super(0, 0, InetAddress.getLoopbackAddress());
            this.remainingFailures = new AtomicInteger(failures);
        }

        @Override
        public Socket accept() throws IOException {
            attempts.add(System.nanoTime());
            if (remainingFailures.getAndDecrement() > 0)
                throw new IOException("simulated accept failure");

            return super.accept();
        }
    }
}
