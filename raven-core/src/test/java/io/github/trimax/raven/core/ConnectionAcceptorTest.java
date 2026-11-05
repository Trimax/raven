package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies that an accept loop ends with its own server socket, so restarting a server does not leave
 * the previous loop spinning on a closed socket.
 */
class ConnectionAcceptorTest {

    @Test
    void acceptLoopEndsWhenItsSocketIsClosedWhileServerIsRunningAgain() throws IOException, InterruptedException {
        // The owning server reports running throughout, as it does after stop() and start()
        final var configuration = RavenServerConfiguration.builder()
                .port(0)
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
                .handshakeTimeout(Duration.ofSeconds(1))
                .build();
        final var acceptor = new ConnectionAcceptor(configuration, () -> true, (_, _) -> {
        });

        final var socket = new ServerSocket(0);
        final var acceptThread = acceptor.start(socket);
        socket.close();

        acceptThread.join(Duration.ofSeconds(2));
        assertFalse(acceptThread.isAlive(), "accept loop must end once its server socket is closed");
    }

    @Test
    void restartedServerAcceptsClients() {
        final var connected = new CopyOnWriteArrayList<Client>();
        final var server = new RavenServer(RavenServerConfiguration.builder()
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                        connected.add(client);
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
        server.stop();
        server.start();

        try {
            final var client = new RavenClient(RavenClientConfiguration.builder()
                    .host("localhost")
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
            client.connect();

            await().atMost(2, TimeUnit.SECONDS).until(() -> connected.size() == 1);
            assertEquals(1, server.getClients().size());

            client.disconnect();
        } finally {
            server.stop();
        }
    }
}
