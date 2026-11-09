package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.internal.session.RawPeer;

/**
 * Verifies that a connection which never completes the handshake does not block other connections.
 */
class RavenServerHandshakeTest {

    @Test
    void silentPeerDoesNotBlockOtherClients() throws Exception {
        final var connects = new CopyOnWriteArrayList<Client>();
        final var server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
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
            // This peer connects at the TCP level but never sends its hello
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
                .bindAddress(InetAddress.getLoopbackAddress())
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
            // The server writes its own hello, then closes the socket after the handshake timeout
            final var in = silent.getInputStream();
            while (in.read() != -1) {
                // drain the server's hello until end-of-stream
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void handshakeCompletedAfterStopDoesNotRegisterClient() throws Exception {
        final var connects = new AtomicInteger();
        final var server = startServer(connects, Duration.ofSeconds(30));

        try (final var peer = new Socket("localhost", server.getPort())) {
            peer.setSoTimeout(5_000);
            // Reading the server's hello guarantees the handshake is in flight
            final var in = peer.getInputStream();
            RawPeer.readHello(in);
            server.stop();

            try {
                RawPeer.writeHello(peer.getOutputStream());
            } catch (final IOException _) {
                // the server may already have closed the socket
            }

            assertClosedByServer(in);
            assertEquals(0, connects.get());
        } finally {
            server.stop();
        }
    }

    @Test
    void stopClosesSocketsStillInHandshake() throws Exception {
        final var connects = new AtomicInteger();
        final var server = startServer(connects, Duration.ofSeconds(30));

        try (final var silent = new Socket("localhost", server.getPort())) {
            final var in = silent.getInputStream();
            RawPeer.readHello(in);
            server.stop();

            // Without stop() closing it, the socket would linger until the 30 second handshake timeout
            silent.setSoTimeout(2_000);
            assertEquals(-1, in.read());
            assertEquals(0, connects.get());
        } finally {
            server.stop();
        }
    }

    /**
     * Asserts that the server closed the connection. When the server closes a socket whose incoming data it has
     * not read yet (here: our hello), the OS sends a reset instead of a FIN, so the read either reports
     * end-of-stream or fails with "Connection reset". A read timeout still fails the test: the socket stayed open.
     */
    private static void assertClosedByServer(final InputStream in) throws IOException {
        try {
            assertEquals(-1, in.read());
        } catch (final SocketException ex) {
            assertTrue(String.valueOf(ex.getMessage()).contains("reset"), "unexpected socket error: " + ex);
        }
    }

        private static RavenServer startServer(final AtomicInteger connects, final Duration handshakeTimeout) {
        final var server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .handshakeTimeout(handshakeTimeout)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                        connects.incrementAndGet();
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
        return server;
    }
}
