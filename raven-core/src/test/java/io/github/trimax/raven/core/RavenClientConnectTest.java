package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;

/**
 * Verifies that a blocked connection attempt does not hold the client's monitor,
 * is bounded by the connect timeout, and is aborted by {@link RavenClient#disconnect()}.
 */
class RavenClientConnectTest {

    @Test
    void blockedConnectAttemptDoesNotBlockOtherCalls() throws Exception {
        // The server accepts the connection but never writes its stream header, so the handshake blocks
        try (final var listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            final var client = newClient(listener.getLocalPort(), Duration.ofSeconds(5), new AtomicInteger());
            final var accepted = acceptAsync(listener);

            final var connecting = CompletableFuture.runAsync(client::connect);
            try (final var ignored = accepted.get(2, TimeUnit.SECONDS)) {
                final long started = System.nanoTime();
                assertFalse(client.isConnected());
                client.disconnect();
                final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

                assertTrue(elapsedMs < 1_000, "isConnected()/disconnect() waited " + elapsedMs + "ms behind connect()");
                // disconnect() aborts the attempt, so connect() returns well before its 5s timeout
                assertDoesNotThrow(() -> connecting.get(2, TimeUnit.SECONDS));
                assertFalse(client.isConnected());
            }
        }
    }

    @Test
    void connectGivesUpAfterConfiguredTimeout() throws Exception {
        try (final var listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            final var connects = new AtomicInteger();
            final var client = newClient(listener.getLocalPort(), Duration.ofMillis(300), connects);
            final var accepted = acceptAsync(listener);

            final long started = System.nanoTime();
            client.connect();
            final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            accepted.get(2, TimeUnit.SECONDS).close();
            assertTrue(elapsedMs < 3_000, "connect() took " + elapsedMs + "ms with a 300ms timeout");
            assertFalse(client.isConnected());
            assertEquals(0, connects.get());
        }
    }

    @Test
    void disconnectDuringConnectLeavesClientDisconnected() throws Exception {
        try (final var listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            final var connects = new AtomicInteger();
            final var client = newClient(listener.getLocalPort(), Duration.ofSeconds(5), connects);
            final var accepted = acceptAsync(listener);

            final var connecting = CompletableFuture.runAsync(client::connect);
            try (final var serverSide = accepted.get(2, TimeUnit.SECONDS)) {
                // The connection exists at TCP level but the handshake is still pending
                client.disconnect();

                // Completing the handshake now must not resurrect the connection
                try {
                    new ObjectOutputStream(serverSide.getOutputStream()).flush();
                } catch (final IOException ignored) {
                    // The client may already have closed its end
                }

                assertDoesNotThrow(() -> connecting.get(2, TimeUnit.SECONDS));
                assertFalse(client.isConnected());
                assertEquals(0, connects.get(), "onConnect must not fire for an attempt cancelled by disconnect()");
            } finally {
                client.disconnect();
            }
        }
    }

    private static CompletableFuture<Socket> acceptAsync(final ServerSocket listener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return listener.accept();
            } catch (final IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    private static RavenClient newClient(final int port, final Duration connectTimeout, final AtomicInteger connects) {
        return new RavenClient(RavenClientConfiguration.builder()
                .host("localhost")
                .port(port)
                .connectTimeout(connectTimeout)
                .handler(new ClientHandler() {
                    @Override
                    public void onConnect() {
                        connects.incrementAndGet();
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
