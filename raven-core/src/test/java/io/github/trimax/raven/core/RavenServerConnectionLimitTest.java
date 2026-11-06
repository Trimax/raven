package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies that {@code maxConnections} bounds the connections a server serves at once and that every
 * connection gives its permit back, however it ends.
 */
class RavenServerConnectionLimitTest {

    private static final int LIMIT = 2;

    private final RavenServer server = new RavenServer(RavenServerConfiguration.builder()
            .bindAddress(InetAddress.getLoopbackAddress())
            .port(0)
            .maxConnections(LIMIT)
            .handler(new ServerHandler() {
                @Override
                public void onConnect(final Client client) {
                }

                @Override
                public void onDisconnect(final Client client) {
                }

                @Override
                public void onMessage(final Client sender, final Message message) {
                }
            })
            .build());

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void connectionBeyondLimitIsClosedByServer() {
        server.start();
        final var first = connectedClient();
        final var second = connectedClient();

        final var third = client();
        third.connect();

        assertFalse(third.isConnected(), "the server must close a connection beyond the limit");
        assertTrue(first.isConnected());
        assertTrue(second.isConnected());
        assertEquals(LIMIT, server.getClients().size());

        first.disconnect();
        second.disconnect();
    }

    @Test
    void endedSessionFreesItsSlot() {
        server.start();
        final var first = connectedClient();
        final var second = connectedClient();

        first.disconnect();
        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 1);

        final var replacement = connectedClient();
        assertTrue(replacement.isConnected());

        replacement.disconnect();
        second.disconnect();
    }

    @Test
    void permitsReturnAfterRepeatedConnectAndDisconnect() {
        server.start();
        for (int i = 0; i < 20; i++) {
            final var client = connectedClient();
            client.disconnect();
            await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10)).atMost(2, TimeUnit.SECONDS)
                    .until(() -> server.availableConnections() == LIMIT);
        }

        assertEquals(LIMIT, server.availableConnections());
    }

    @Test
    void permitsReturnWhenHandshakeFails() throws IOException {
        server.start();
        // A raw socket that closes without a stream header fails the handshake
        for (int i = 0; i < 5; i++)
            new Socket(InetAddress.getLoopbackAddress(), server.getPort()).close();

        await().atMost(2, TimeUnit.SECONDS).until(() -> server.availableConnections() == LIMIT);
    }

    @Test
    void permitsReturnAfterStop() {
        server.start();
        connectedClient();
        connectedClient();
        assertEquals(0, server.availableConnections());

        server.stop();
        assertEquals(LIMIT, server.availableConnections());

        server.start();
        final var first = connectedClient();
        final var second = connectedClient();
        assertEquals(LIMIT, server.getClients().size());

        first.disconnect();
        second.disconnect();
    }

    private RavenClient connectedClient() {
        final var client = client();
        client.connect();
        assertTrue(client.isConnected(), "connection within the limit must be accepted");
        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == LIMIT - server.availableConnections());
        return client;
    }

    private RavenClient client() {
        return new RavenClient(RavenClientConfiguration.builder()
                .host(InetAddress.getLoopbackAddress().getHostAddress())
                .port(server.getPort())
                .handler(new ClientHandler() {
                    @Override
                    public void onConnect() {
                    }

                    @Override
                    public void onDisconnect() {
                    }

                    @Override
                    public void onMessage(final Message message) {
                    }
                })
                .build());
    }
}
