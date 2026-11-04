package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies that {@link RavenServer#stop()} racing with client disconnects reports each disconnect once.
 */
class RavenServerStopTest {

    private static final int ITERATIONS = 20;
    private static final int CLIENTS_PER_ITERATION = 5;

    @Test
    void onDisconnectIsReportedOncePerClientWhenStopRacesWithClientLoops() throws InterruptedException {
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            final Map<UUID, AtomicInteger> disconnects = new ConcurrentHashMap<>();
            final var server = new RavenServer(RavenServerConfiguration.builder()
                    .port(0)
                    .handler(new ServerHandler() {
                        @Override
                        public void onConnect(final Client client) {
                        }

                        @Override
                        public void onDisconnect(final Client client) {
                            disconnects.computeIfAbsent(client.getId(), id -> new AtomicInteger()).incrementAndGet();
                        }

                        @Override
                        public void onMessage(final Client sender, final Message message) {
                        }
                    })
                    .build());
            server.start();

            final List<RavenClient> clients = new ArrayList<>();
            try {
                for (int i = 0; i < CLIENTS_PER_ITERATION; i++) {
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
                    clients.add(client);
                }
                await().atMost(5, TimeUnit.SECONDS).until(() -> server.getClients().size() == CLIENTS_PER_ITERATION);

                // Clients drop their sockets while the server is stopping
                clients.forEach(RavenClient::disconnect);
                server.stop();

                // Give the receive loops time to observe the closed sockets and race with stop()
                Thread.sleep(100);

                for (final var count : disconnects.values())
                    assertEquals(1, count.get(), "onDisconnect must run exactly once per client, iteration " + iteration);
                assertEquals(CLIENTS_PER_ITERATION, disconnects.size(), "every client must be reported, iteration " + iteration);
            } finally {
                clients.forEach(RavenClient::disconnect);
                server.stop();
            }
        }
    }
}
