package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies that a connection which never completes the handshake does not block other connections.
 */
class RavenServerHandshakeTest {

    @Test
    void silentPeerDoesNotBlockOtherClients() throws Exception {
        final var connects = new CopyOnWriteArrayList<Client>();
        final var server = new RavenServer(RavenServerConfiguration.builder()
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                        connects.add(client);
                    }

                    @Override
                    public void onDisconnect(final Client client) {
                    }

                    @Override
                    public void onMessage(final Client sender, final Message message) {
                    }
                })
                .build());
        server.start();

        try (final var silent = new Socket("localhost", server.getPort())) {
            // This peer connects at the TCP level but never sends its stream header
            final var clientConnected = new java.util.concurrent.atomic.AtomicBoolean(false);
            final var client = new RavenClient(RavenClientConfiguration.builder()
                    .host("localhost")
                    .port(server.getPort())
                    .handler(new ClientHandler() {
                        @Override
                        public void onConnect() {
                            clientConnected.set(true);
                        }

                        @Override
                        public void onDisconnect() {
                        }

                        @Override
                        public void onMessage(final Message message) {
                        }
                    })
                    .build());

            client.connect();

            await().atMost(2, TimeUnit.SECONDS).until(() -> !connects.isEmpty());
            assertTrue(clientConnected.get());
            client.disconnect();
        } finally {
            server.stop();
        }
    }

    @Test
    void silentPeerIsDroppedAfterHandshakeTimeout() throws Exception {
        final var server = new RavenServer(RavenServerConfiguration.builder()
                .port(0)
                .handshakeTimeout(Duration.ofMillis(500))
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
        server.start();

        try (final var silent = new Socket("localhost", server.getPort())) {
            silent.setSoTimeout(5_000);
            // The server writes its own stream header, then closes the socket after the handshake timeout
            final var in = silent.getInputStream();
            while (in.read() != -1) {
                // drain the server's stream header until end-of-stream
            }
        } finally {
            server.stop();
        }
    }
}
