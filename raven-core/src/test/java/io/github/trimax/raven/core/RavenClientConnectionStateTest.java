package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies the client's connection state when its connect handler fails.
 */
class RavenClientConnectionStateTest {

    @Test
    void connectHandlerFailureLeavesClientDisconnected() throws Exception {
        final var server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
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
                .build());
        server.start();

        final var client = new RavenClient(RavenClientConfiguration.builder()
                .host("localhost")
                .port(server.getPort())
                .handler(new ClientHandler() {
                    @Override
                    public void onConnect() {
                        throw new IllegalStateException("handler failure");
                    }

                    @Override
                    public void onDisconnect() {
                    }

                    @Override
                    public void onMessage(final Message message) {
                    }
                })
                .build());

        try {
            assertDoesNotThrow(client::connect);

            assertFalse(client.isConnected());
            // The server must notice that the client went away instead of keeping a dead session
            await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().isEmpty());
            assertDoesNotThrow(() -> client.send(new TestMessage("ignored")));
        } finally {
            client.disconnect();
            server.stop();
        }
    }
}
