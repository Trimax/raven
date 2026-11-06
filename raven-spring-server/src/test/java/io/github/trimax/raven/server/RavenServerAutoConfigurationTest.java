package io.github.trimax.raven.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;

/**
 * Verifies that the {@code raven.server.*} properties are applied to the created server.
 */
class RavenServerAutoConfigurationTest {

    @Test
    void appliesConfiguredBindAddress() throws Exception {
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, null, null, null).ravenServer(new ServerMessageRouter(), interceptors());
        server.start();
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            assertTrue(socket.isConnected());
        } finally {
            server.stop();
        }
    }

    @Test
    void acceptsMissingBindAddress() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null);

        assertDoesNotThrow(() -> configuration.ravenServer(new ServerMessageRouter(), interceptors()));
    }

    @Test
    void rejectsUnresolvableBindAddress() {
        final var configuration = new RavenServerAutoConfiguration(0, "no such host.invalid", null, null, null, null, null);

        final var ex = assertThrows(IllegalArgumentException.class,
                () -> configuration.ravenServer(new ServerMessageRouter(), interceptors()));
        assertEquals("Invalid raven.server.bind-address: no such host.invalid", ex.getMessage());
    }

    @Test
    void rejectsZeroMaxConnections() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, 0, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors()));
    }

    @Test
    void appliesConfiguredIdleTimeout() throws Exception {
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, null, null, "300ms")
                .ravenServer(new ServerMessageRouter(), interceptors());
        server.start();
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            // Completes the handshake, then stays silent
            new ObjectOutputStream(socket.getOutputStream()).flush();
            new ObjectInputStream(socket.getInputStream());
            socket.setSoTimeout(3_000);

            assertEquals(-1, socket.getInputStream().read(), "the server must close the idle connection");
        } finally {
            server.stop();
        }
    }

    @Test
    void appliesConfiguredHandshakeTimeout() throws Exception {
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, "300ms", null, null)
                .ravenServer(new ServerMessageRouter(), interceptors());
        server.start();
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            // Never sends a stream header, so only the handshake timeout ends the connection
            new ObjectInputStream(socket.getInputStream());
            socket.setSoTimeout(3_000);

            final long started = System.nanoTime();
            assertEquals(-1, socket.getInputStream().read(), "the server must drop a connection that does not complete the handshake");
            final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMs < 2_000, "dropped after " + elapsedMs + "ms with a 300ms handshake timeout");
        } finally {
            server.stop();
        }
    }

    @Test
    void rejectsZeroHandshakeTimeout() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, "0s", null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors()));
    }

    @Test
    void rejectsMalformedIdleTimeout() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, "never");

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors()));
    }

    private static ObjectProvider<ServerMessageInterceptor> interceptors() {
        return new DefaultListableBeanFactory().getBeanProvider(ServerMessageInterceptor.class);
    }
}
