package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.ServerSocket;
import java.net.Socket;
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
            listener = new ServerSocket(0);
            final var accepted = CompletableFuture.supplyAsync(() -> {
                try {
                    return new Connection(listener.accept());
                } catch (final Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            final Socket raw = new Socket("localhost", listener.getLocalPort());
            client = new Connection(raw);
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
    void sendDoesNotBlockOnSlowPeerAndDisconnectsIt() throws Exception {
        try (final var pair = new Pair()) {
            // The server side never calls receive(), so the socket buffers fill up and the writer stalls.
            final var payload = "x".repeat(16 * 1024);
            final long started = System.nanoTime();

            for (int i = 0; i < Connection.SEND_QUEUE_CAPACITY * 4; i++)
                pair.client.send(new TestMessage(payload));

            final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMs < 5_000, "send() must not wait for the network, took " + elapsedMs + "ms");
            await().atMost(5, TimeUnit.SECONDS).until(() -> !pair.client.isConnected());
        }
    }
}
