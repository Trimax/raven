package io.github.trimax.raven.core.validation;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.RavenClient;
import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.core.exception.MessageValidationRavenException;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.validation.annotation.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Integration tests verifying that message validation is applied on both send and receive paths.
 */
class ValidationIntegrationTest {

    private static final String HOST = "localhost";

    private RavenServer server;
    private int port;
    private final List<Message> serverReceivedMessages = new CopyOnWriteArrayList<>();
    private final List<Client> connectedClients = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        server = new RavenServer(0, new ServerHandler() {
            @Override
            public void onConnect(final Client client) {
                connectedClients.add(client);
            }

            @Override
            public void onDisconnect(final Client client) {
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
    void clientSendingInvalidMessageThrowsException() {
        final var client = createClient();
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(client::isConnected);

        assertThrows(MessageValidationRavenException.class, () -> client.send(new InvalidMessage()));

        // Server should never receive the message
        assertDoesNotArrive(serverReceivedMessages::isEmpty);

        client.disconnect();
    }

    @Test
    void clientSendingValidMessageIsReceivedByServer() {
        final var client = createClient();
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(client::isConnected);

        client.send(new ValidMessage("hello"));

        await().atMost(2, TimeUnit.SECONDS).until(() -> !serverReceivedMessages.isEmpty());

        assertEquals(1, serverReceivedMessages.size());
        assertInstanceOf(ValidMessage.class, serverReceivedMessages.getFirst());
        assertEquals("hello", ((ValidMessage) serverReceivedMessages.getFirst()).getContent());

        client.disconnect();
    }

    @Test
    void serverSendingInvalidMessageThrowsException() {
        final var clientReceivedMessages = new CopyOnWriteArrayList<Message>();

        final var client = new RavenClient(HOST, port, new ClientHandler() {
            @Override
            public void onConnect() {
            }

            @Override
            public void onDisconnect() {
            }

            @Override
            public void onMessage(final Message message) {
                clientReceivedMessages.add(message);
            }
        });
        client.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> !connectedClients.isEmpty());

        assertThrows(MessageValidationRavenException.class, () -> server.broadcast(new InvalidMessage()));

        // Client should never receive the message
        assertDoesNotArrive(clientReceivedMessages::isEmpty);

        client.disconnect();
    }

    @Test
    void receivingInvalidMessageBypassingSendValidationDropsMessage() throws Exception {
        // Connect a legitimate client first so the server has time to set up
        final var legitimateClient = createClient();
        legitimateClient.connect();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !connectedClients.isEmpty());
        legitimateClient.disconnect();
        await().atMost(2, TimeUnit.SECONDS).until(() -> connectedClients.size() == 1);

        // Clear any state from the first connection
        serverReceivedMessages.clear();
        connectedClients.clear();

        // Use a raw socket to bypass RavenClient's send method validation
        try (final var socket = new Socket(HOST, port)) {
            final var oos = new ObjectOutputStream(socket.getOutputStream());

            // Wait for the server to register this raw connection
            await().atMost(2, TimeUnit.SECONDS).until(() -> !connectedClients.isEmpty());

            // Send an invalid message directly (bypasses MessageValidator.validateOrThrow)
            oos.writeObject(new InvalidMessage());
            oos.flush();

            // Server receive loop should validate and drop the message — handler NOT called
            assertDoesNotArrive(serverReceivedMessages::isEmpty);
        }
    }

    /**
     * Asserts that a condition remains true for a reasonable period (the message never arrives).
     */
    private void assertDoesNotArrive(final BooleanSupplier stillEmpty) {
        try {
            Thread.sleep(500);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(stillEmpty.getAsBoolean(), "Expected no messages to arrive but some did");
    }

    private RavenClient createClient() {
        return new RavenClient(HOST, port, new NoOpClientHandler());
    }

    private static class NoOpClientHandler implements ClientHandler {
        @Override
        public void onConnect() {
        }

        @Override
        public void onDisconnect() {
        }

        @Override
        public void onMessage(final Message message) {
        }
    }

    /**
     * A message with a valid @NotBlank field.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class ValidMessage extends Message {
        @NotBlank
        private String content;
    }

    /**
     * A message where the @NotBlank field is null by default — always fails validation.
     */
    @NoArgsConstructor
    static class InvalidMessage extends Message {
        @NotBlank
        @SuppressWarnings("unused")
        private String content;
    }
}
