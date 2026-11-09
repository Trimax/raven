package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.internal.session.RawPeer;
import io.github.trimax.raven.core.internal.session.Session;

/**
 * Verifies end to end that the network input filter only admits {@link Message} subclasses and safe JDK types, and
 * that a peer sending anything else is disconnected. The filter's rules are covered by
 * {@link io.github.trimax.raven.core.codec.JavaSerializationCodecTest}.
 */
class DeserializationFilterTest {

    private final List<Message> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger disconnects = new AtomicInteger();
    private RavenServer server;

    @BeforeEach
    void startServer() {
        server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                    }

                    @Override
                    public void onDisconnect(final Client client) {
                        disconnects.incrementAndGet();
                    }

                    @Override
                    public void onMessage(final Client sender, final Message message) {
                        received.add(message);
                    }
                })
                .build());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void messageSubclassIsAccepted() throws Exception {
        try (final var peer = connect()) {
            RawPeer.writeObject(peer.getOutputStream(), new TestMessage("hello"));

            await().atMost(2, TimeUnit.SECONDS).until(() -> received.size() == 1);
            assertEquals("hello", ((TestMessage) received.getFirst()).getContent());
        }
    }

    @Test
    void rejectedClassClosesTheConnection() throws Exception {
        assertDisconnectedAfterSending(new Date());
    }

    @Test
    void allowedObjectThatIsNotAMessageClosesTheConnection() throws Exception {
        assertDisconnectedAfterSending("I am not a Message");
    }

    @Test
    void rejectedClassIsLoggedWithTheHint() throws Exception {
        final var logger = (Logger) LoggerFactory.getLogger(Session.class);
        final var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertDisconnectedAfterSending(new PayloadMessage());

            final var warnings = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertEquals(1, warnings.size(), warnings.toString());
            final var warning = warnings.getFirst();
            assertTrue(warning.startsWith("Rejected class " + Payload.class.getName() + " in a message from "), warning);
            assertTrue(warning.contains("closing the connection"), warning);
            assertTrue(warning.contains("raven.server.trusted-packages / raven.client.trusted-packages=io.github.trimax.raven.core"), warning);
            assertTrue(warning.contains("new JavaSerializationCodec(Set.of(\"io.github.trimax.raven.core\"))"), warning);
        } finally {
            logger.detachAppender(appender);
        }
    }

    /** Serializable, but neither a Message, a record nor an allowed JDK type. */
    static final class Payload implements Serializable {
    }

    static final class PayloadMessage extends Message {
        @SuppressWarnings("unused")
        private final Payload payload = new Payload();
    }

    private void assertDisconnectedAfterSending(final Object object) throws IOException {
        try (final var peer = connect()) {
            RawPeer.writeObject(peer.getOutputStream(), object);

            assertClosedByServer(peer.getInputStream());
            await().atMost(2, TimeUnit.SECONDS).until(() -> disconnects.get() == 1);
            assertTrue(received.isEmpty());
        }
    }

    /**
     * Opens a raw connection and completes the handshake, so the server announces it.
     */
    private Socket connect() throws IOException {
        final var peer = new Socket(InetAddress.getLoopbackAddress(), server.getPort());
        peer.setSoTimeout(5_000);
        RawPeer.writeHello(peer.getOutputStream());
        RawPeer.readHello(peer.getInputStream());
        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 1);
        return peer;
    }

    private static void assertClosedByServer(final InputStream in) throws IOException {
        try {
            assertEquals(-1, in.read());
        } catch (final SocketException ex) {
            assertTrue(String.valueOf(ex.getMessage()).contains("reset"), "unexpected socket error: " + ex);
        }
    }
}
