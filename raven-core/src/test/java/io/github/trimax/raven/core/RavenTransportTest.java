package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Integration tests for the Raven transport layer (RavenServer + RavenClient).
 */
class RavenTransportTest {

    private static final String HOST = "localhost";

    private RavenServer server;
    private int port;
    private final List<Message> serverReceivedMessages = new CopyOnWriteArrayList<>();
    private final List<Client> connectedClients = new CopyOnWriteArrayList<>();
    private final List<Client> disconnectedClients = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        server = new RavenServer(0, new ServerHandler() {
            @Override
            public void onConnect(final Client client) {
                connectedClients.add(client);
            }

            @Override
            public void onDisconnect(final Client client) {
                disconnectedClients.add(client);
            }

            @Override
            public void onMessage(final Client sender, final Message message) {
                serverReceivedMessages.add(message);
            }
        });
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void clientConnectsToServer() {
        final var client = createClient();
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> !connectedClients.isEmpty());

        assertTrue(client.isConnected());
        assertEquals(1, connectedClients.size());
        assertEquals(1, server.getClients().size());

        client.disconnect();
    }

    @Test
    void clientSendsMessageToServer() {
        final var client = createClient();
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(client::isConnected);

        client.send(new TestMessage("hello"));

        await().atMost(2, TimeUnit.SECONDS).until(() -> !serverReceivedMessages.isEmpty());

        assertEquals(1, serverReceivedMessages.size());
        assertInstanceOf(TestMessage.class, serverReceivedMessages.getFirst());
        assertEquals("hello", ((TestMessage) serverReceivedMessages.getFirst()).getContent());

        client.disconnect();
    }

    @Test
    void serverSendsMessageToClient() throws InterruptedException {
        final var receivedMessages = new CopyOnWriteArrayList<Message>();
        final var latch = new CountDownLatch(1);

        final var client = new RavenClient(HOST, port, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {
                receivedMessages.add(message);
                latch.countDown();
            }
        });
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> !connectedClients.isEmpty());

        server.send(new TestMessage("from server"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, receivedMessages.size());
        assertEquals("from server", ((TestMessage) receivedMessages.getFirst()).getContent());

        client.disconnect();
    }

    @Test
    void serverBroadcastsToAllClients() throws InterruptedException {
        final var received1 = new CopyOnWriteArrayList<Message>();
        final var received2 = new CopyOnWriteArrayList<Message>();
        final var latch = new CountDownLatch(2);

        final var client1 = new RavenClient(HOST, port, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {
                received1.add(message);
                latch.countDown();
            }
        });
        final var client2 = new RavenClient(HOST, port, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {
                received2.add(message);
                latch.countDown();
            }
        });

        client1.connect();
        client2.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> connectedClients.size() == 2);

        server.send(new TestMessage("broadcast"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("broadcast", ((TestMessage) received1.getFirst()).getContent());
        assertEquals("broadcast", ((TestMessage) received2.getFirst()).getContent());

        client1.disconnect();
        client2.disconnect();
    }

    @Test
    void serverSendsToSpecificClient() {
        final var received1 = new CopyOnWriteArrayList<Message>();
        final var received2 = new CopyOnWriteArrayList<Message>();

        final var client1 = new RavenClient(HOST, port, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {
                received1.add(message);
            }
        });
        final var client2 = new RavenClient(HOST, port, new ClientHandler() {
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

        await().atMost(2, TimeUnit.SECONDS).until(() -> connectedClients.size() == 2);

        final var targetId = connectedClients.getFirst().getId();
        server.send(new TestMessage("targeted"), targetId);

        await().atMost(2, TimeUnit.SECONDS).until(() -> received1.size() + received2.size() == 1);

        assertEquals(1, received1.size() + received2.size());

        client1.disconnect();
        client2.disconnect();
    }

    @Test
    void clientDisconnectNotifiesServer() {
        final var client = createClient();
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> !connectedClients.isEmpty());

        client.disconnect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> !disconnectedClients.isEmpty());
        assertEquals(1, disconnectedClients.size());
        assertTrue(server.getClients().isEmpty());
    }

    @Test
    void serverStopDisconnectsAllClients() {
        final var client = createClient();
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(client::isConnected);

        server.stop();

        await().atMost(2, TimeUnit.SECONDS).until(() -> !client.isConnected());
    }

    @Test
    void multipleMessagesInSequence() {
        final var client = createClient();
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(client::isConnected);

        for (int i = 0; i < 10; i++) {
            client.send(new TestMessage("msg-" + i));
        }

        await().atMost(5, TimeUnit.SECONDS).until(() -> serverReceivedMessages.size() == 10);

        for (int i = 0; i < 10; i++) {
            assertEquals("msg-" + i, ((TestMessage) serverReceivedMessages.get(i)).getContent());
        }

        client.disconnect();
    }

    @Test
    void multipleClientsConnectSimultaneously() {
        final var clients = new java.util.ArrayList<RavenClient>();
        for (int i = 0; i < 5; i++) {
            final var client = createClient();
            client.connect();
            clients.add(client);
        }

        await().atMost(5, TimeUnit.SECONDS).until(() -> connectedClients.size() == 5);
        assertEquals(5, server.getClients().size());

        clients.forEach(RavenClient::disconnect);
    }

    @Test
    void serverSendsToMultipleSpecificRecipients() {
        final var received1 = new CopyOnWriteArrayList<Message>();
        final var received2 = new CopyOnWriteArrayList<Message>();
        final var received3 = new CopyOnWriteArrayList<Message>();

        final var client1 = new RavenClient(HOST, port, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {
                received1.add(message);
            }
        });
        final var client2 = new RavenClient(HOST, port, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {
                received2.add(message);
            }
        });
        final var client3 = new RavenClient(HOST, port, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {
                received3.add(message);
            }
        });

        client1.connect();
        client2.connect();
        client3.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> connectedClients.size() == 3);

        // Send to client1 and client2 only, not client3
        final var target1 = connectedClients.get(0).getId();
        final var target2 = connectedClients.get(1).getId();
        server.send(new TestMessage("multi-target"), target1, target2);

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> received1.size() + received2.size() == 2);

        // Third client should NOT receive the message
        try {
            Thread.sleep(300);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(received3.isEmpty(), "Client3 should not receive a targeted message");

        client1.disconnect();
        client2.disconnect();
        client3.disconnect();
    }

    @Test
    void connectToNonExistentServerFails() {
        final var client = new RavenClient(HOST, 19999, new NoOpClientHandler());
        client.connect();
        assertFalse(client.isConnected());
    }

    private RavenClient createClient() {
        return new RavenClient(HOST, port, new NoOpClientHandler());
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
