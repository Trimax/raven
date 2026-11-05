package io.github.trimax.raven.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.Socket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;

/**
 * Verifies that {@code raven.server.bind-address} is applied to the created server.
 */
class RavenServerAutoConfigurationTest {

    @Test
    void appliesConfiguredBindAddress() throws Exception {
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null).ravenServer(new ServerMessageRouter(), interceptors());
        server.start();
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            assertTrue(socket.isConnected());
        } finally {
            server.stop();
        }
    }

    @Test
    void acceptsMissingBindAddress() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null);

        assertDoesNotThrow(() -> configuration.ravenServer(new ServerMessageRouter(), interceptors()));
    }

    @Test
    void rejectsUnresolvableBindAddress() {
        final var configuration = new RavenServerAutoConfiguration(0, "no such host.invalid", null, null);

        final var ex = assertThrows(IllegalArgumentException.class,
                () -> configuration.ravenServer(new ServerMessageRouter(), interceptors()));
        assertEquals("Invalid raven.server.bind-address: no such host.invalid", ex.getMessage());
    }

    private static ObjectProvider<ServerMessageInterceptor> interceptors() {
        return new DefaultListableBeanFactory().getBeanProvider(ServerMessageInterceptor.class);
    }
}
