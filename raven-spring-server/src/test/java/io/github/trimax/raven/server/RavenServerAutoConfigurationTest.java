package io.github.trimax.raven.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.codec.JavaSerializationCodec;
import io.github.trimax.raven.core.codec.MessageCodec;
import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;

/**
 * Verifies that the {@code raven.server.*} properties are applied to the created server.
 */
class RavenServerAutoConfigurationTest {

    @Test
    void appliesConfiguredBindAddress() throws Exception {
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, null, null, null, null).ravenServer(new ServerMessageRouter(), interceptors(), codecs());
        server.start();
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            assertTrue(socket.isConnected());
        } finally {
            server.stop();
        }
    }

    @Test
    void acceptsMissingBindAddress() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs()));
    }

    @Test
    void rejectsUnresolvableBindAddress() {
        final var configuration = new RavenServerAutoConfiguration(0, "no such host.invalid", null, null, null, null, null, null);

        final var ex = assertThrows(IllegalArgumentException.class,
                () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs()));
        assertEquals("Invalid raven.server.bind-address: no such host.invalid", ex.getMessage());
    }

    @Test
    void rejectsZeroMaxConnections() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, 0, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs()));
    }

    @Test
    void appliesConfiguredIdleTimeout() throws Exception {
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, null, null, "300ms", null)
                .ravenServer(new ServerMessageRouter(), interceptors(), codecs());
        server.start();
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            // Completes the handshake, then stays silent
            writeHello(socket);
            socket.setSoTimeout(3_000);
            assertEquals(8, socket.getInputStream().readNBytes(8).length, "the server's hello");

            assertEquals(-1, socket.getInputStream().read(), "the server must close the idle connection");
        } finally {
            server.stop();
        }
    }

    @Test
    void appliesConfiguredHandshakeTimeout() throws Exception {
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, "300ms", null, null, null)
                .ravenServer(new ServerMessageRouter(), interceptors(), codecs());
        server.start();
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            // Never sends a hello, so only the handshake timeout ends the connection
            socket.setSoTimeout(3_000);
            assertEquals(8, socket.getInputStream().readNBytes(8).length, "the server's hello");

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
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, "0s", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs()));
    }

    @Test
    void rejectsMalformedIdleTimeout() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, "never", null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs()));
    }

    @Test
    void acceptsMaxMessageSizeWithUnit() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, "2KB");

        assertDoesNotThrow(() -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs()));
    }

    @Test
    void rejectsMalformedMaxMessageSize() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, "two kilobytes");

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs()));
    }

    @Test
    void rejectsZeroMaxMessageSize() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, "0B");

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs()));
    }

    @Test
    void usesMessageCodecBean() {
        final var encodes = new AtomicInteger();
        final var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("codec", new MessageCodec() {
            private final JavaSerializationCodec delegate = new JavaSerializationCodec();

            @Override
            public byte[] encode(final Message message) throws IOException {
                encodes.incrementAndGet();
                return delegate.encode(message);
            }

            @Override
            public Message decode(final byte[] payload) throws IOException {
                return delegate.decode(payload);
            }
        });
        final var server = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, null)
                .ravenServer(new ServerMessageRouter(), interceptors(), beans.getBeanProvider(MessageCodec.class));

        // A broadcast is encoded even when nobody is connected
        server.broadcast(new Ping());

        assertEquals(1, encodes.get());
    }

    /** A message without fields. */
    static final class Ping extends Message {
    }

    /**
     * Sends the protocol hello: the magic bytes and the protocol version.
     */
    private static void writeHello(final Socket socket) throws IOException {
        final var output = new DataOutputStream(socket.getOutputStream());
        output.writeBytes("RAVN");
        output.writeInt(2);
        output.flush();
    }

    private static ObjectProvider<MessageCodec> codecs() {
        return new DefaultListableBeanFactory().getBeanProvider(MessageCodec.class);
    }

    private static ObjectProvider<ServerMessageInterceptor> interceptors() {
        return new DefaultListableBeanFactory().getBeanProvider(ServerMessageInterceptor.class);
    }
}
