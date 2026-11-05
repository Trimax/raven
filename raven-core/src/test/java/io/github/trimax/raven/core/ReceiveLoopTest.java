package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReceiveLoop}.
 */
class ReceiveLoopTest {

    /** A connected pair of Connections over loopback: the loop reads from {@code reader}, the test writes to {@code peer}. */
    private static final class Pair implements AutoCloseable {
        private final ServerSocket listener;
        private final Connection reader;
        private final Connection peer;

        Pair() throws Exception {
            listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            final var accepted = CompletableFuture.supplyAsync(() -> {
                try {
                    return TestConnections.open(listener.accept());
                } catch (final Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            peer = TestConnections.open(new Socket("localhost", listener.getLocalPort()));
            reader = accepted.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            reader.disconnect();
            peer.disconnect();
            listener.close();
        }
    }

    /** Records whether the connection was already disconnected when {@code onEnded} ran. */
    private static final class Ended implements Runnable {
        private final Connection connection;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicBoolean disconnectedFirst = new AtomicBoolean();

        Ended(final Connection connection) {
            this.connection = connection;
        }

        @Override
        public void run() {
            disconnectedFirst.set(!connection.isConnected());
            calls.incrementAndGet();
        }
    }

    @Test
    void endsWhenPeerCloses() throws Exception {
        try (final var pair = new Pair()) {
            final var delivered = new CopyOnWriteArrayList<Message>();
            final var ended = new Ended(pair.reader);

            pair.peer.send(new TestMessage("one"));
            final var loop = start(pair.reader, () -> true, delivered::add, ended);
            await().atMost(5, TimeUnit.SECONDS).until(() -> delivered.size() == 1);
            pair.peer.disconnect();

            assertTrue(loop.join(Duration.ofSeconds(5)), "loop must end once the peer closes");
            assertEquals(1, delivered.size());
            assertEnded(ended);
        }
    }

    @Test
    void endsWhenDeliverReturnsFalse() throws Exception {
        try (final var pair = new Pair()) {
            final var delivered = new AtomicInteger();
            final var ended = new Ended(pair.reader);

            pair.peer.send(new TestMessage("one"));
            pair.peer.send(new TestMessage("two"));
            final var loop = start(pair.reader, () -> true, _ -> {
                delivered.incrementAndGet();
                return false;
            }, ended);

            assertTrue(loop.join(Duration.ofSeconds(5)), "loop must end once deliver returns false");
            assertEquals(1, delivered.get(), "no message is delivered after deliver returned false");
            assertEnded(ended);
        }
    }

    @Test
    void endsWhenNotRunning() throws Exception {
        try (final var pair = new Pair()) {
            final var delivered = new AtomicInteger();
            final var ended = new Ended(pair.reader);

            pair.peer.send(new TestMessage("one"));
            final var loop = start(pair.reader, () -> false, _ -> delivered.incrementAndGet() > 0, ended);

            assertTrue(loop.join(Duration.ofSeconds(5)), "loop must not read once running is false");
            assertEquals(0, delivered.get());
            assertEnded(ended);
        }
    }

    private static Thread start(final Connection connection, final BooleanSupplier running,
                                final Predicate<Message> deliver, final Runnable onEnded) {
        return Thread.ofVirtual().start(() -> ReceiveLoop.run(connection, running, deliver, onEnded));
    }

    private static void assertEnded(final Ended ended) {
        assertEquals(1, ended.calls.get(), "onEnded must run exactly once");
        assertTrue(ended.disconnectedFirst.get(), "the connection must be disconnected before onEnded runs");
    }
}
