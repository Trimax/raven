package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Verifies the asynchronous, bounded send path of {@link Connection}.
 */
class ConnectionSendTest {

    /** A connected pair of Connections over loopback. */
    private static final class Pair implements AutoCloseable {
        private final ServerSocket listener;
        private final Connection client;
        private final Connection server;

        Pair() throws Exception {
            this(TestConnections.QUEUE_CAPACITY, TestConnections.SEND_TIMEOUT);
        }

        Pair(final int clientQueueCapacity, final Duration clientSendTimeout) throws Exception {
            listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            final var accepted = CompletableFuture.supplyAsync(() -> {
                try {
                    return TestConnections.open(listener.accept());
                } catch (final Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            final Socket raw = new Socket("localhost", listener.getLocalPort());
            client = Connection.open(raw, TestConnections.HANDSHAKE_TIMEOUT, clientQueueCapacity, clientSendTimeout);
            server = accepted.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            client.disconnect();
            server.disconnect();
            listener.close();
        }
    }

    @Test
    void messagesArriveInSendOrder() throws Exception {
        try (final var pair = new Pair()) {
            final int count = 200;
            for (int i = 0; i < count; i++)
                pair.client.send(new TestMessage(String.valueOf(i)));

            for (int i = 0; i < count; i++) {
                final var received = pair.server.receive();
                assertInstanceOf(TestMessage.class, received);
                assertEquals(String.valueOf(i), ((TestMessage) received).getContent());
            }
        }
    }

    @Test
    void burstToReadingPeerIsFullyDelivered() throws Exception {
        try (final var pair = new Pair()) {
            final int count = 5_000;
            final var reader = CompletableFuture.supplyAsync(() -> {
                for (int i = 0; i < count; i++) {
                    final var received = pair.server.receive();
                    if (!(received instanceof TestMessage message) || !String.valueOf(i).equals(message.getContent()))
                        return i;
                }
                return count;
            });

            for (int i = 0; i < count; i++)
                pair.client.send(new TestMessage(String.valueOf(i)));

            assertEquals(count, reader.get(30, TimeUnit.SECONDS), "every message must arrive in order");
            assertTrue(pair.client.isConnected(), "a peer that keeps reading must not be disconnected");
        }
    }

    @Test
    void sendIsBoundedAndDisconnectsStalledPeer() throws Exception {
        final long sendTimeoutMs = 200;
        try (final var pair = new Pair(4, Duration.ofMillis(sendTimeoutMs))) {
            // The server side never calls receive(), so the socket buffers fill up and the writer stalls.
            final var payload = "x".repeat(256 * 1024);

            for (int i = 0; i < 10_000 && pair.client.isConnected(); i++) {
                final long started = System.nanoTime();
                pair.client.send(new TestMessage(payload));
                final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                assertTrue(elapsedMs < sendTimeoutMs + 2_000, "send() must be bounded by the send timeout, took " + elapsedMs + "ms");
            }

            assertFalse(pair.client.isConnected(), "a peer that stops reading must be disconnected");
            assertTrue(pair.client.writer.join(Duration.ofSeconds(5)), "writer thread must terminate");
        }
    }
}
