package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Unit/integration tests for {@link RavenServer} focusing on lifecycle and edge cases.
 */
class RavenServerTest {

    @Test
    void startAndStop() {
        final var server = new RavenServer(0, new NoOpServerHandler());

        server.start();
        assertTrue(server.isRunning());
        assertTrue(server.getPort() > 0);

        server.stop();
        assertFalse(server.isRunning());
    }

    @Test
    void doubleStartIsIdempotent() {
        final var server = new RavenServer(0, new NoOpServerHandler());

        server.start();
        server.start(); // should not throw or create duplicate listeners

        assertTrue(server.isRunning());
        server.stop();
    }

    @Test
    void doubleStopIsIdempotent() {
        final var server = new RavenServer(0, new NoOpServerHandler());
        server.start();

        server.stop();
        assertDoesNotThrow(server::stop);
    }

    @Test
    void stopBeforeStartIsNoOp() {
        final var server = new RavenServer(0, new NoOpServerHandler());
        assertDoesNotThrow(server::stop);
        assertFalse(server.isRunning());
    }

    @Test
    void getPortReturnsNegativeOneWhenNotRunning() {
        final var server = new RavenServer(0, new NoOpServerHandler());
        assertEquals(-1, server.getPort());
    }

    @Test
    void getPortReturnsNegativeOneAfterStop() {
        final var server = new RavenServer(0, new NoOpServerHandler());
        server.start();
        assertTrue(server.getPort() > 0);

        server.stop();
        assertEquals(-1, server.getPort());
    }

    @Test
    void getClientsEmptyInitially() {
        final var server = new RavenServer(0, new NoOpServerHandler());
        server.start();

        assertTrue(server.getClients().isEmpty());
        server.stop();
    }

    @Test
    void getClientsReflectsConnectedClients() {
        final var server = new RavenServer(0, new NoOpServerHandler());
        server.start();

        final var client = new RavenClient("localhost", server.getPort(), new NoOpClientHandler());
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 1);
        assertEquals(1, server.getClients().size());

        client.disconnect();
        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().isEmpty());

        server.stop();
    }

    @Test
    void sendToNonExistentClientLogsWarning() {
        final var server = new RavenServer(0, new NoOpServerHandler());
        server.start();

        assertDoesNotThrow(() -> server.send(new TestMessage("hi"), java.util.UUID.randomUUID()));

        server.stop();
    }

    @Test
    void broadcastToEmptyServerIsNoOp() {
        final var server = new RavenServer(0, new NoOpServerHandler());
        server.start();

        assertDoesNotThrow(() -> server.broadcast(new TestMessage("broadcast to nobody")));

        server.stop();
    }

    @Test
    void onDisconnectCalledForEachClientOnStop() {
        final var disconnected = new CopyOnWriteArrayList<Client>();
        final var server = new RavenServer(0, new ServerHandler() {
            @Override
            public void onConnect(final Client client) {}

            @Override
            public void onDisconnect(final Client client) { disconnected.add(client); }

            @Override
            public void onMessage(final Client sender, final Message message) {}
        });
        server.start();

        final var port = server.getPort();
        final var c1 = new RavenClient("localhost", port, new NoOpClientHandler());
        final var c2 = new RavenClient("localhost", port, new NoOpClientHandler());
        c1.connect();
        c2.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 2);

        server.stop();

        await().atMost(2, TimeUnit.SECONDS).until(() -> disconnected.size() >= 2);
    }

    @Test
    void sendWithEmptyRecipientsIsNoOp() {
        final var received = new CopyOnWriteArrayList<Message>();

        final var server = new RavenServer(0, new NoOpServerHandler());
        server.start();

        final var client = new RavenClient("localhost", server.getPort(), new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {
                received.add(message);
            }
        });
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(client::isConnected);

        // Sending message with empty array — should NOT reach the client
        server.send(new TestMessage("should not arrive"), new java.util.UUID[0]);

        // Wait briefly and verify nothing arrived
        try {
            Thread.sleep(300);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(received.isEmpty(), "send() with empty recipients should be a no-op");

        client.disconnect();
        server.stop();
    }

    @Test
    void broadcastReachesAllClients() {
        final var received1 = new CopyOnWriteArrayList<Message>();
        final var received2 = new CopyOnWriteArrayList<Message>();

        final var server = new RavenServer(0, new NoOpServerHandler());
        server.start();

        final var client1 = new RavenClient("localhost", server.getPort(), new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {
                received1.add(message);
            }
        });
        final var client2 = new RavenClient("localhost", server.getPort(), new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {
                received2.add(message);
            }
        });

        client1.connect();
        client2.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 2);

        server.broadcast(new TestMessage("hello all"));

        await().atMost(2, TimeUnit.SECONDS).until(() -> received1.size() == 1 && received2.size() == 1);
        assertEquals("hello all", ((TestMessage) received1.getFirst()).getContent());
        assertEquals("hello all", ((TestMessage) received2.getFirst()).getContent());

        client1.disconnect();
        client2.disconnect();
        server.stop();
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
