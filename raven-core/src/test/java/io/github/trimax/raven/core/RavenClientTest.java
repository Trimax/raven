package io.github.trimax.raven.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

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
        server = new RavenServer(0, new NoOpServerHandler());
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void connectToRunningServer() {
        final var connected = new AtomicBoolean(false);
        final var client = new RavenClient("localhost", port, new ClientHandler() {
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
        final var client = new RavenClient("localhost", 19999, new NoOpClientHandler());
        client.connect();
        assertFalse(client.isConnected());
    }

    @Test
    void doubleConnectIsIdempotent() {
        final var connectCount = new AtomicInteger(0);
        final var client = new RavenClient("localhost", port, new ClientHandler() {
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
        final var client = new RavenClient("localhost", port, new ClientHandler() {
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
        final var client = new RavenClient("localhost", port, new ClientHandler() {
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
        final var client = new RavenClient("localhost", port, new NoOpClientHandler());
        assertDoesNotThrow(client::disconnect);
    }

    @Test
    void sendWhenNotConnectedLogsWarning() {
        final var client = new RavenClient("localhost", port, new NoOpClientHandler());
        assertDoesNotThrow(() -> client.send(new TestMessage("should not crash")));
    }

    @Test
    void serverShutdownTriggersClientDisconnect() throws InterruptedException {
        final var disconnectLatch = new CountDownLatch(1);
        final var client = new RavenClient("localhost", port, new ClientHandler() {
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
        final var client = new RavenClient("localhost", port, new NoOpClientHandler());

        client.connect();
        assertTrue(client.isConnected());

        client.disconnect();
        assertFalse(client.isConnected());

        client.connect();
        assertTrue(client.isConnected());

        client.disconnect();
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
