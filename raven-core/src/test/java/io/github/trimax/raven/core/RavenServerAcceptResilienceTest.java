package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies that an exception thrown by the server handler for one connection does not stop the accept loop.
 */
class RavenServerAcceptResilienceTest {

    @Test
    void handlerExceptionDoesNotStopAcceptingConnections() {
        final var connectCalls = new AtomicInteger();
        final var connected = new CopyOnWriteArrayList<Client>();

        final var server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                        if (connectCalls.incrementAndGet() == 1)
                            throw new IllegalStateException("handler failure on first connection");
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

        try {
            final var first = createClient(server.getPort());
            first.connect();
            await().atMost(2, TimeUnit.SECONDS).until(() -> connectCalls.get() >= 1);

            final var second = createClient(server.getPort());
            second.connect();

            await().atMost(2, TimeUnit.SECONDS).until(() -> !connected.isEmpty());
            assertTrue(server.isRunning());
            assertEquals(1, server.getClients().size());

            second.disconnect();
            first.disconnect();
        } finally {
            server.stop();
        }
    }

    private static RavenClient createClient(final int port) {
        return new RavenClient(RavenClientConfiguration.builder()
                .host("localhost")
                .port(port)
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
