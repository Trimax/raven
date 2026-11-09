package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.codec.JavaSerializationCodec;
import io.github.trimax.raven.core.codec.MessageCodec;
import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.type.DataSize;

/**
 * Verifies that {@code send()} encodes on the calling thread: serialization errors reach the caller, a message is
 * sent as it was at the call, a broadcast is encoded once, and the configured codec is used on both sides.
 */
class RavenSendEncodingTest {

    private static final int MAX_MESSAGE_SIZE = 4096;

    private final List<Message> serverReceived = new CopyOnWriteArrayList<>();
    private final List<Message> clientReceived = new CopyOnWriteArrayList<>();
    private final AtomicInteger encodes = new AtomicInteger();
    private final AtomicInteger decodes = new AtomicInteger();

    /** Counts its calls, so the tests see when and how often messages are encoded. */
    private final MessageCodec codec = new MessageCodec() {
        private final JavaSerializationCodec delegate = new JavaSerializationCodec();

        @Override
        public byte[] encode(final Message message) throws IOException {
            encodes.incrementAndGet();
            return delegate.encode(message);
        }

        @Override
        public Message decode(final byte[] payload) throws IOException {
            decodes.incrementAndGet();
            return delegate.decode(payload);
        }
    };

    private RavenServer server;

    @BeforeEach
    void startServer() {
        server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .codec(codec)
                .maxMessageSize(DataSize.ofBytes(MAX_MESSAGE_SIZE))
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                    }

                    @Override
                    public void onDisconnect(final Client client) {
                    }

                    @Override
                    public void onMessage(final Client sender, final Message message) {
                        serverReceived.add(message);
                    }
                })
                .build());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void serializationErrorIsThrownToTheCaller() {
        final var client = connectedClient();

        final var ex = assertThrows(IllegalArgumentException.class, () -> client.send(new UnserializableMessage()));
        assertTrue(ex.getMessage().contains(UnserializableMessage.class.getName()));
        assertThrows(IllegalArgumentException.class, () -> server.broadcast(new UnserializableMessage()));
        assertTrue(client.isConnected(), "a message that cannot be serialized must not cost the connection");

        client.disconnect();
    }

    @Test
    void messageLargerThanTheLimitIsThrownToTheCaller() {
        final var client = connectedClient();

        assertThrows(IllegalArgumentException.class, () -> client.send(new TestMessage("x".repeat(MAX_MESSAGE_SIZE))));
        assertTrue(client.isConnected());

        client.disconnect();
    }

    @Test
    void messageChangedAfterSendArrivesAsItWasAtSend() {
        final var client = connectedClient();
        final var message = new MutableMessage("before");

        client.send(message);
        message.content = "after";

        await().atMost(2, TimeUnit.SECONDS).until(() -> serverReceived.size() == 1);
        assertEquals("before", ((MutableMessage) serverReceived.getFirst()).content);

        client.disconnect();
    }

    @Test
    void broadcastIsEncodedOnceForAllRecipients() {
        final var first = connectedClient();
        final var second = connectedClient();
        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 2);
        encodes.set(0);

        server.broadcast(new TestMessage("to all"));

        await().atMost(2, TimeUnit.SECONDS).until(() -> clientReceived.size() == 2);
        assertEquals(1, encodes.get());
        assertTrue(decodes.get() >= 2, "both sides use the configured codec");

        first.disconnect();
        second.disconnect();
    }

    private RavenClient connectedClient() {
        final var client = new RavenClient(RavenClientConfiguration.builder()
                .host(InetAddress.getLoopbackAddress().getHostAddress())
                .port(server.getPort())
                .codec(codec)
                .maxMessageSize(DataSize.ofBytes(MAX_MESSAGE_SIZE))
                .handler(new ClientHandler() {
                    @Override
                    public void onConnect() {
                    }

                    @Override
                    public void onDisconnect() {
                    }

                    @Override
                    public void onMessage(final Message message) {
                        clientReceived.add(message);
                    }
                })
                .build());
        client.connect();
        assertTrue(client.isConnected());
        return client;
    }

    /** A message whose content can change after it was sent. */
    static final class MutableMessage extends Message {
        private String content;

        MutableMessage(final String content) {
            this.content = content;
        }
    }

    /** A message with a field Java serialization cannot write. */
    static final class UnserializableMessage extends Message {
        @SuppressWarnings({"unused", "serial"})
        private final Object notSerializable = new Object();
    }
}
