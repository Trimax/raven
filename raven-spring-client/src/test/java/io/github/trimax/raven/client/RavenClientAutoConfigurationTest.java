package io.github.trimax.raven.client;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import io.github.trimax.raven.core.interceptor.ClientMessageInterceptor;

/**
 * Verifies that the optional {@code raven.client.*} properties are applied to the created client.
 */
class RavenClientAutoConfigurationTest {

    @Test
    void appliesConfiguredConnectTimeout() throws Exception {
        // The listener never accepts at application level, so only the connect timeout ends the handshake
        try (final var listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            final var configuration = new RavenClientAutoConfiguration("localhost", listener.getLocalPort(), "300ms", null, null, null);
            final var client = configuration.ravenClient(new ClientMessageRouter(), interceptors());

            final long started = System.nanoTime();
            client.connect();
            final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertTrue(elapsedMs < 3_000, "connect() took " + elapsedMs + "ms with a 300ms timeout");
            assertFalse(client.isConnected());
        }
    }

    @Test
    void acceptsMissingConnectTimeout() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, null, null);

        assertDoesNotThrow(() -> configuration.ravenClient(new ClientMessageRouter(), interceptors()));
    }

    @Test
    void rejectsMalformedConnectTimeout() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, "soon", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors()));
    }

    @Test
    void rejectsZeroSendQueueCapacity() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, 0, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors()));
    }

    @Test
    void rejectsMalformedSendTimeout() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, "later", null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors()));
    }

    @Test
    void rejectsMalformedIdleTimeout() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, null, "never");

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors()));
    }

    @Test
    void rejectsNegativeIdleTimeout() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, null, "-1s");

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors()));
    }

    private static ObjectProvider<ClientMessageInterceptor> interceptors() {
        return new DefaultListableBeanFactory().getBeanProvider(ClientMessageInterceptor.class);
    }
}
