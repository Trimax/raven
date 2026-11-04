package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;

/**
 * Verifies that a blocked connection attempt does not hold the client's monitor.
 */
class RavenClientConnectTest {

    @Test
    void blockedConnectAttemptDoesNotBlockOtherCalls() throws Exception {
        // The listener accepts TCP connections at the kernel level but the application never accepts them,
        // so the stream handshake blocks until the client's timeout expires.
        try (final var listener = new ServerSocket(0)) {
            final var client = new RavenClient(RavenClientConfiguration.builder()
                    .host("localhost")
                    .port(listener.getLocalPort())
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

            final var connecting = CompletableFuture.runAsync(client::connect);
            Thread.sleep(500);

            final long started = System.nanoTime();
            assertFalse(client.isConnected());
            client.disconnect();
            final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertTrue(elapsedMs < 1_000, "isConnected()/disconnect() waited " + elapsedMs + "ms behind connect()");
            assertDoesNotThrow(() -> connecting.get(RavenClient.CONNECT_TIMEOUT_MS + 5_000L, TimeUnit.MILLISECONDS));
            assertFalse(client.isConnected());
        }
    }
}
