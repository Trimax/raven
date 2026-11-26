package io.github.trimax.raven.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;

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
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, null, null, null, null, null, null).ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts());
        server.start();
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            assertTrue(socket.isConnected());
        } finally {
            server.stop();
        }
    }

    @Test
    void acceptsTlsOnlyWithAnSslContextBean() throws Exception {
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, null, null, null, null, null, null)
                .ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContext());
        server.start();
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            writeHello(socket);
            socket.setSoTimeout(3_000);
            // A plain Raven hello is no TLS handshake: the server answers with an alert or closes, never with its own hello
            final int first = socket.getInputStream().read();
            assertTrue(first != 'R', "the server answered a plain hello with its own");
        } finally {
            server.stop();
        }
    }

    @Test
    void answersInPlainTextWithoutAnSslContextBean() throws Exception {
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, null, null, null, null, null, null)
                .ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts());
        server.start();
        try (final var socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            socket.setSoTimeout(3_000);
            assertEquals('R', socket.getInputStream().read());
        } finally {
            server.stop();
        }
    }

    @Test
    void rejectsClientCertificateRequirementWithoutSslContextBean() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, null, null, true);

        assertThrows(IllegalArgumentException.class,
                () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void acceptsClientCertificateRequirementWithSslContextBean() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, null, null, true);

        assertDoesNotThrow(() -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContext()));
    }

    @Test
    void acceptsMissingBindAddress() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsUnresolvableBindAddress() {
        final var configuration = new RavenServerAutoConfiguration(0, "no such host.invalid", null, null, null, null, null, null, null, null);

        final var ex = assertThrows(IllegalArgumentException.class,
                () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts()));
        assertEquals("Invalid raven.server.bind-address: no such host.invalid", ex.getMessage());
    }

    @Test
    void rejectsZeroMaxConnections() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, 0, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void appliesConfiguredIdleTimeout() throws Exception {
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, null, null, "300ms", null, null, null)
                .ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts());
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
        final var server = new RavenServerAutoConfiguration(0, "127.0.0.1", null, null, "300ms", null, null, null, null, null)
                .ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts());
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
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, "0s", null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsMalformedIdleTimeout() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, "never", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void acceptsMaxMessageSizeWithUnit() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, "2KB", null, null);

        assertDoesNotThrow(() -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsMalformedMaxMessageSize() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, "two kilobytes", null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsZeroMaxMessageSize() {
        final var configuration = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, "0B", null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenServer(new ServerMessageRouter(), interceptors(), codecs(), sslContexts()));
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
        final var server = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, null, null, null)
                .ravenServer(new ServerMessageRouter(), interceptors(), beans.getBeanProvider(MessageCodec.class), sslContexts());

        // A broadcast is encoded even when nobody is connected
        server.broadcast(new Ping());

        assertEquals(1, encodes.get());
    }

    @Test
    void parsesTrustedPackagesIntoTheDefaultCodec() {
        final var codec = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, null, " com.example, org.acme,,com.example ", null).codec(codecs());

        assertEquals(Set.of("com.example", "org.acme"), assertInstanceOf(JavaSerializationCodec.class, codec).getTrustedPackages());
    }

    @Test
    void trustsNoPackagesWhenUnset() {
        final var codec = new RavenServerAutoConfiguration(0, null, null, null, null, null, null, null, "", null).codec(codecs());

        assertEquals(Set.of(), assertInstanceOf(JavaSerializationCodec.class, codec).getTrustedPackages());
    }

    @Test
    void ignoresTrustedPackagesForAMessageCodecBean() {
        final var bean = new JavaSerializationCodec();
        final var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("codec", bean);

        assertSame(bean, new RavenServerAutoConfiguration(0, null, null, null, null, null, null, null, "com.example", null).codec(beans.getBeanProvider(MessageCodec.class)));
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

    private static ObjectProvider<SSLContext> sslContexts() {
        return new DefaultListableBeanFactory().getBeanProvider(SSLContext.class);
    }

    private static ObjectProvider<SSLContext> sslContext() throws NoSuchAlgorithmException {
        final var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("sslContext", SSLContext.getDefault());
        return beans.getBeanProvider(SSLContext.class);
    }

    private static ObjectProvider<MessageCodec> codecs() {
        return new DefaultListableBeanFactory().getBeanProvider(MessageCodec.class);
    }

    private static ObjectProvider<ServerMessageInterceptor> interceptors() {
        return new DefaultListableBeanFactory().getBeanProvider(ServerMessageInterceptor.class);
    }
}
