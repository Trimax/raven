package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Unit/integration tests for {@link RavenClient} focusing on connect, disconnect, and edge cases.
 */
class RavenClientTest {

    private RavenServer server;
    private int port;

    @BeforeEach
    void setUp() {
        server = createServer(new NoOpServerHandler());
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void nullConfigurationThrowsNpe() {
        assertThrows(NullPointerException.class, () -> new RavenClient(null));
    }

    @Test
    void connectToRunningServer() {
        final var connected = new AtomicBoolean(false);
        final var client = createClient("localhost", port, new ClientHandler() {
            @Override
            public void onConnect() { connected.set(true); }

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {}
        });

        client.connect();

        assertTrue(client.isConnected());
        assertTrue(connected.get());
        client.disconnect();
    }

    @Test
    void connectToNonExistentServerFails() {
        final var client = createClient("localhost", 19999, new NoOpClientHandler());
        client.connect();
        assertFalse(client.isConnected());
    }

    @Test
    void doubleConnectIsIdempotent() {
        final var connectCount = new AtomicInteger(0);
        final var client = createClient("localhost", port, new ClientHandler() {
            @Override
            public void onConnect() { connectCount.incrementAndGet(); }

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {}
        });

        client.connect();
        client.connect();

        assertEquals(1, connectCount.get());
        client.disconnect();
    }

    @Test
    void disconnectCallsHandler() {
        final var disconnected = new AtomicBoolean(false);
        final var client = createClient("localhost", port, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() { disconnected.set(true); }

            @Override
            public void onMessage(final Message message) {}
        });

        client.connect();
        assertTrue(client.isConnected());

        client.disconnect();
        assertFalse(client.isConnected());
        assertTrue(disconnected.get());
    }

    @Test
    void doubleDisconnectIsIdempotent() {
        final var disconnectCount = new AtomicInteger(0);
        final var client = createClient("localhost", port, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() { disconnectCount.incrementAndGet(); }

            @Override
            public void onMessage(final Message message) {}
        });

        client.connect();
        client.disconnect();
        client.disconnect();

        assertEquals(1, disconnectCount.get());
    }

    @Test
    void disconnectWhenNotConnectedIsNoOp() {
        final var client = createClient("localhost", port, new NoOpClientHandler());
        assertDoesNotThrow(client::disconnect);
    }

    @Test
    void sendWhenNotConnectedLogsWarning() {
        final var client = createClient("localhost", port, new NoOpClientHandler());
        assertDoesNotThrow(() -> client.send(new TestMessage("should not crash")));
    }

    @Test
    void serverShutdownTriggersClientDisconnect() throws InterruptedException {
        final var disconnectLatch = new CountDownLatch(1);
        final var client = createClient("localhost", port, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() { disconnectLatch.countDown(); }

            @Override
            public void onMessage(final Message message) {}
        });

        client.connect();
        await().atMost(2, TimeUnit.SECONDS).until(client::isConnected);

        server.stop();

        assertTrue(disconnectLatch.await(5, TimeUnit.SECONDS));
        assertFalse(client.isConnected());
    }

    @Test
    void reconnectAfterDisconnect() {
        final var client = createClient("localhost", port, new NoOpClientHandler());

        client.connect();
        assertTrue(client.isConnected());

        client.disconnect();
        assertFalse(client.isConnected());

        client.connect();
        assertTrue(client.isConnected());

        client.disconnect();
    }

    @Test
    void concurrentSendDuringDisconnectDoesNotThrow() {
        final var client = createClient("localhost", port, new NoOpClientHandler());
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(client::isConnected);

        // Launch multiple threads sending concurrently while disconnect happens
        final var threads = new java.util.ArrayList<Thread>();
        for (int i = 0; i < 10; i++) {
            final var idx = i;
            threads.add(Thread.ofVirtual().start(() ->
                    assertDoesNotThrow(() -> client.send(new TestMessage("concurrent-" + idx)))));
        }

        // Disconnect while sends may still be in-flight
        assertDoesNotThrow(client::disconnect);

        // Wait for all sender threads to complete without exceptions
        for (final var thread : threads) {
            assertDoesNotThrow(() -> thread.join(2000));
        }
    }

    private static RavenServer createServer(final ServerHandler handler) {
        final var config = RavenServerConfiguration.builder()
                .port(0)
                .handler(handler)
                .build();
        return new RavenServer(config);
    }

    private static RavenClient createClient(final String host, final int port, final ClientHandler handler) {
        final var config = RavenClientConfiguration.builder()
                .host(host)
                .port(port)
                .handler(handler)
                .build();
        return new RavenClient(config);
    }

    private static class NoOpServerHandler implements ServerHandler {
        @Override
        public void onConnect(final Client client) {}

        @Override
        public void onDisconnect(final Client client) {}

        @Override
        public void onMessage(final Client sender, final Message message) {}
    }

    private static class NoOpClientHandler implements ClientHandler {
        @Override
        public void onConnect() {}

        @Override
        public void onDisconnect() {}

        @Override
        public void onMessage(final Message message) {}
    }
}
