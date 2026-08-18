package io.github.trimax.raven;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import io.github.trimax.raven.handler.ClientHandler;
import io.github.trimax.raven.handler.ServerHandler;

/**
 * Unit/integration tests for {@link RavenServer} focusing on lifecycle and edge cases.
 */
class RavenServerTest {

    private static final int PORT = 19092;

    @Test
    void startAndStop() {
        final var server = new RavenServer(PORT, new NoOpServerHandler());

        server.start();
        assertTrue(server.isRunning());

        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void doubleStartIsIdempotent() {
        final var server = new RavenServer(PORT, new NoOpServerHandler());

        server.start();
        server.start(); // should not throw or create duplicate listeners

        assertTrue(server.isRunning());
        server.stop();
    }

    @Test
    void doubleStopIsIdempotent() {
        final var server = new RavenServer(PORT, new NoOpServerHandler());
        server.start();

        server.stop();
        assertDoesNotThrow(server::stop);
    }

    @Test
    void stopBeforeStartIsNoOp() {
        final var server = new RavenServer(PORT, new NoOpServerHandler());
        assertDoesNotThrow(server::stop);
        assertFalse(server.isRunning());
    }

    @Test
    void getClientsEmptyInitially() {
        final var server = new RavenServer(PORT, new NoOpServerHandler());
        server.start();

        assertTrue(server.getClients().isEmpty());
        server.stop();
    }

    @Test
    void getClientsReflectsConnectedClients() {
        final var server = new RavenServer(PORT, new NoOpServerHandler());
        server.start();

        final var client = new RavenClient("localhost", PORT, new NoOpClientHandler());
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 1);
        assertEquals(1, server.getClients().size());

        client.disconnect();
        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().isEmpty());

        server.stop();
    }

    @Test
    void sendToNonExistentClientLogsWarning() {
        final var server = new RavenServer(PORT, new NoOpServerHandler());
        server.start();

        assertDoesNotThrow(() -> server.send(new TestMessage("hi"), java.util.UUID.randomUUID()));

        server.stop();
    }

    @Test
    void broadcastToEmptyServerIsNoOp() {
        final var server = new RavenServer(PORT, new NoOpServerHandler());
        server.start();

        assertDoesNotThrow(() -> server.send(new TestMessage("broadcast to nobody")));

        server.stop();
    }

    @Test
    void onDisconnectCalledForEachClientOnStop() {
        final var disconnected = new CopyOnWriteArrayList<Client>();
        final var server = new RavenServer(PORT, new ServerHandler() {
            @Override
            public void onConnect(final Client client) {}

            @Override
            public void onDisconnect(final Client client) { disconnected.add(client); }

            @Override
            public void onMessage(final Client sender, final Message message) {}
        });
        server.start();

        final var c1 = new RavenClient("localhost", PORT, new NoOpClientHandler());
        final var c2 = new RavenClient("localhost", PORT, new NoOpClientHandler());
        c1.connect();
        c2.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 2);

        server.stop();

        await().atMost(2, TimeUnit.SECONDS).until(() -> disconnected.size() >= 2);
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
