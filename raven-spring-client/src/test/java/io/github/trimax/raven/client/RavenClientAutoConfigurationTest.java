package io.github.trimax.raven.client;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import io.github.trimax.raven.core.codec.JavaSerializationCodec;
import io.github.trimax.raven.core.codec.MessageCodec;
import io.github.trimax.raven.core.interceptor.ClientMessageInterceptor;

/**
 * Verifies that the optional {@code raven.client.*} properties are applied to the created client.
 */
class RavenClientAutoConfigurationTest {

    @Test
    void appliesConfiguredConnectTimeout() throws Exception {
        // The listener never accepts at application level, so only the connect timeout ends the handshake
        try (final var listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            final var configuration = new RavenClientAutoConfiguration("localhost", listener.getLocalPort(), "300ms", null, null, null, null, null, null);
            final var client = configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts());

            final long started = System.nanoTime();
            client.connect();
            final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertTrue(elapsedMs < 3_000, "connect() took " + elapsedMs + "ms with a 300ms timeout");
            assertFalse(client.isConnected());
        }
    }

    @Test
    void connectsOverTlsWithAnSslContextBean() throws Exception {
        try (final var listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            final var configuration = new RavenClientAutoConfiguration("localhost", listener.getLocalPort(), "1s", null, null, null, null, null, null);
            final var client = configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContext());

            final var connecting = new Thread(client::connect);
            connecting.start();
            try (final var accepted = listener.accept()) {
                accepted.setSoTimeout(3_000);
                // The first byte of a TLS record: a handshake. A plain client would start with the 'R' of its hello
                assertEquals(0x16, accepted.getInputStream().read());
            } finally {
                connecting.join();
                client.disconnect();
            }
        }
    }

    @Test
    void connectsInPlainTextWithoutAnSslContextBean() throws Exception {
        try (final var listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            final var configuration = new RavenClientAutoConfiguration("localhost", listener.getLocalPort(), "1s", null, null, null, null, null, null);
            final var client = configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts());

            final var connecting = new Thread(client::connect);
            connecting.start();
            try (final var accepted = listener.accept()) {
                accepted.setSoTimeout(3_000);
                assertEquals('R', accepted.getInputStream().read());
            } finally {
                connecting.join();
                client.disconnect();
            }
        }
    }

    @Test
    void acceptsMissingConnectTimeout() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsMalformedConnectTimeout() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, "soon", null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsZeroSendQueueCapacity() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, 0, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsMalformedSendTimeout() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, "later", null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsMalformedIdleTimeout() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, null, "never", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsNegativeIdleTimeout() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, null, "-1s", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void acceptsMaxMessageSizeWithUnit() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, null, null, "2KB", null, null);

        assertDoesNotThrow(() -> configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsMalformedMaxMessageSize() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, null, null, "two kilobytes", null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void rejectsZeroMaxMessageSize() {
        final var configuration = new RavenClientAutoConfiguration("localhost", 0, null, null, null, null, "0B", null, null);

        assertThrows(IllegalArgumentException.class, () -> configuration.ravenClient(new ClientMessageRouter(), interceptors(), codecs(), sslContexts()));
    }

    @Test
    void parsesTrustedPackagesIntoTheDefaultCodec() {
        final var codec = new RavenClientAutoConfiguration("localhost", 0, null, null, null, null, null, " com.example, org.acme,,com.example ", null).codec(codecs());

        assertEquals(Set.of("com.example", "org.acme"), assertInstanceOf(JavaSerializationCodec.class, codec).getTrustedPackages());
    }

    @Test
    void trustsNoPackagesWhenUnset() {
        final var codec = new RavenClientAutoConfiguration("localhost", 0, null, null, null, null, null, "", null).codec(codecs());

        assertEquals(Set.of(), assertInstanceOf(JavaSerializationCodec.class, codec).getTrustedPackages());
    }

    @Test
    void ignoresTrustedPackagesForAMessageCodecBean() {
        final var bean = new JavaSerializationCodec();
        final var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("codec", bean);

        assertSame(bean, new RavenClientAutoConfiguration("localhost", 0, null, null, null, null, null, "com.example", null).codec(beans.getBeanProvider(MessageCodec.class)));
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

    private static ObjectProvider<ClientMessageInterceptor> interceptors() {
        return new DefaultListableBeanFactory().getBeanProvider(ClientMessageInterceptor.class);
    }
}
