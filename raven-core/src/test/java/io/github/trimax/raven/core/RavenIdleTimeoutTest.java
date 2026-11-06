package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies the optional idle timeout: a connection that receives nothing for that long is closed and reported as
 * disconnected, while without it a silent connection stays open.
 */
class RavenIdleTimeoutTest {

    private static final Duration IDLE_TIMEOUT = Duration.ofMillis(300);

    private final AtomicInteger serverDisconnects = new AtomicInteger();
    private final AtomicInteger clientDisconnects = new AtomicInteger();

    private RavenServer server;
    private RavenClient client;

    @AfterEach
    void tearDown() {
        if (client != null)
            client.disconnect();
        if (server != null)
            server.stop();
    }

    @Test
    void serverClosesSilentConnection() {
        start(IDLE_TIMEOUT, Duration.ZERO);

        await().atMost(3, TimeUnit.SECONDS).until(() -> serverDisconnects.get() == 1);
        await().atMost(3, TimeUnit.SECONDS).until(() -> clientDisconnects.get() == 1);
        assertTrue(server.getClients().isEmpty());
        assertFalse(client.isConnected());
    }

    @Test
    void clientClosesSilentConnection() {
        start(Duration.ZERO, IDLE_TIMEOUT);

        await().atMost(3, TimeUnit.SECONDS).until(() -> clientDisconnects.get() == 1);
        await().atMost(3, TimeUnit.SECONDS).until(() -> serverDisconnects.get() == 1);
        assertFalse(client.isConnected());
    }

    @Test
    void silentConnectionSurvivesWithoutIdleTimeout() throws InterruptedException {
        start(Duration.ZERO, Duration.ZERO);

        Thread.sleep(1_200);

        assertTrue(client.isConnected());
        assertEquals(1, server.getClients().size());
        assertEquals(0, serverDisconnects.get());
        assertEquals(0, clientDisconnects.get());
    }

    private void start(final Duration serverIdleTimeout, final Duration clientIdleTimeout) {
        server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .idleTimeout(serverIdleTimeout)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client connected) {
                    }

                    @Override
                    public void onDisconnect(final Client disconnected) {
                        serverDisconnects.incrementAndGet();
                    }

                    @Override
                    public void onMessage(final Client sender, final Message message) {
                    }
                })
                .build());
        server.start();

        client = new RavenClient(RavenClientConfiguration.builder()
                .host(InetAddress.getLoopbackAddress().getHostAddress())
                .port(server.getPort())
                .idleTimeout(clientIdleTimeout)
                .handler(new ClientHandler() {
                    @Override
                    public void onConnect() {
                    }

                    @Override
                    public void onDisconnect() {
                        clientDisconnects.incrementAndGet();
                    }

                    @Override
                    public void onMessage(final Message message) {
                    }
                })
                .build());
        client.connect();
        assertTrue(client.isConnected());
    }
}
