package io.github.trimax.raven.core;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
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

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ServerHandler;

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
