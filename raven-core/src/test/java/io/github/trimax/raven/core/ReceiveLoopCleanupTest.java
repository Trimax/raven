package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies that a receive loop releases its connection when a handler throws.
 */
class ReceiveLoopCleanupTest {

    @Test
    void serverHandlerFailureDisconnectsClientAndNotifies() throws Exception {
        final var disconnects = new AtomicInteger();
        final var server = new RavenServer(RavenServerConfiguration.builder()
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                    }

                    @Override
                    public void onDisconnect(final Client client) {
                        disconnects.incrementAndGet();
                    }

                    @Override
                    public void onMessage(final Client sender, final Message message) {
                        throw new IllegalStateException("handler failure");
                    }
                })
                .build());
        server.start();

        final var client = new RavenClient(RavenClientConfiguration.builder()
                .host("localhost")
                .port(server.getPort())
                .handler(noOpClientHandler())
                .build());

        try {
            client.connect();
            client.send(new TestMessage("boom"));

            await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().isEmpty());
            assertEquals(1, disconnects.get());
        } finally {
            client.disconnect();
            server.stop();
        }
    }

    @Test
    void clientHandlerFailureDisconnectsAndNotifies() throws Exception {
        final var disconnects = new AtomicInteger();
        final var server = new RavenServer(RavenServerConfiguration.builder()
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
                    }

                    @Override
                    public void onDisconnect() {
                        disconnects.incrementAndGet();
                    }

                    @Override
                    public void onMessage(final Message message) {
                        throw new IllegalStateException("handler failure");
                    }
                })
                .build());

        try {
            client.connect();
            await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 1);

            server.broadcast(new TestMessage("boom"));

            await().atMost(2, TimeUnit.SECONDS).until(() -> !client.isConnected());
            assertEquals(1, disconnects.get());
        } finally {
            client.disconnect();
            server.stop();
        }
    }

    private static ClientHandler noOpClientHandler() {
        return new ClientHandler() {
            @Override
            public void onConnect() {
            }

            @Override
            public void onDisconnect() {
            }

            @Override
            public void onMessage(final Message message) {
            }
        };
    }
}
