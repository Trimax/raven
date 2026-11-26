package io.github.trimax.raven.core;

import static io.github.trimax.raven.core.TlsFixture.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * TLS between {@link RavenServer} and {@link RavenClient}, with certificates generated for the test run.
 */
class RavenTlsTest {

    private final List<Message> serverReceived = new CopyOnWriteArrayList<>();
    private final List<Message> clientReceived = new CopyOnWriteArrayList<>();
    private final List<Client> serverConnected = new CopyOnWriteArrayList<>();

    private RavenServer server;
    private RavenClient client;

    @AfterEach
    void tearDown() {
        if (client != null)
            client.disconnect();
        if (server != null)
            server.stop();
    }

    @Test
    void messagesTravelBothWaysOverTls() {
        startServer(context(SERVER, SERVER), false);
        connect("localhost", trustingOnly(SERVER), true);

        await().atMost(5, TimeUnit.SECONDS).until(client::isConnected);
        client.send(new TestMessage("to server"));
        await().atMost(5, TimeUnit.SECONDS).until(() -> !serverReceived.isEmpty());
        assertEquals("to server", ((TestMessage) serverReceived.getFirst()).getContent());

        server.broadcast(new TestMessage("to client"));
        await().atMost(5, TimeUnit.SECONDS).until(() -> !clientReceived.isEmpty());
        assertEquals("to client", ((TestMessage) clientReceived.getFirst()).getContent());
    }

    @Test
    void connectsByIpAddressThatTheCertificateNames() {
        startServer(context(SERVER, SERVER), false);
        connect("127.0.0.1", trustingOnly(SERVER), true);

        assertTrue(client.isConnected());
    }

    @Test
    void clientCertificateIsAcceptedWhenRequired() {
        startServer(context(SERVER, CLIENT), true);
        connect("localhost", context(CLIENT, SERVER), true);

        assertTrue(client.isConnected());
        await().atMost(5, TimeUnit.SECONDS).until(() -> !serverConnected.isEmpty());
    }

    @Test
    void clientWithoutCertificateIsRejectedWhenRequired() {
        startServer(context(SERVER, CLIENT), true);
        connect("localhost", trustingOnly(SERVER), true);

        assertRejected();
    }

    @Test
    void clientWithUntrustedCertificateIsRejectedWhenRequired() {
        startServer(context(SERVER, CLIENT), true);
        connect("localhost", context(STRANGER, SERVER), true);

        assertRejected();
    }

    @Test
    void clientWithoutCertificateIsAcceptedWhenNotRequired() {
        startServer(context(SERVER), false);
        connect("localhost", trustingOnly(SERVER), true);

        assertTrue(client.isConnected());
    }

    @Test
    void untrustedServerCertificateIsRejected() {
        startServer(context(STRANGER, STRANGER), false);
        connect("localhost", trustingOnly(SERVER), true);

        assertRejected();
    }

    @Test
    void certificateOfAnotherHostIsRejected() {
        startServer(context(OTHER_HOST, OTHER_HOST), false);
        connect("localhost", trustingOnly(OTHER_HOST), true);

        assertRejected();
    }

    @Test
    void certificateOfAnotherHostIsAcceptedWithoutHostnameVerification() {
        startServer(context(OTHER_HOST, OTHER_HOST), false);
        connect("localhost", trustingOnly(OTHER_HOST), false);

        assertTrue(client.isConnected());
    }

    @Test
    void plainClientCannotUseTlsServer() {
        startServer(context(SERVER), false);
        connect("localhost", null, true);

        assertRejected();
    }

    @Test
    void tlsClientCannotUsePlainServer() {
        startServer(null, false);
        connect("localhost", trustingOnly(SERVER), true);

        assertRejected();
    }

    @Test
    void clientCertificateRequirementNeedsSslContext() {
        final var configuration = RavenServerConfiguration.builder()
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {}

                    @Override
                    public void onDisconnect(final Client client) {}

                    @Override
                    public void onMessage(final Client sender, final Message message) {}
                })
                .requireClientCertificate(true)
                .build();

        final var ex = assertThrows(IllegalArgumentException.class, () -> new RavenServer(configuration));
        assertTrue(ex.getMessage().contains("sslContext"), ex.getMessage());
    }

    private void assertRejected() {
        assertFalse(client.isConnected());
        assertTrue(serverConnected.isEmpty());
        assertEquals(0, server.getClients().size());
    }

    private void startServer(final SSLContext sslContext, final boolean requireClientCertificate) {
        server = new RavenServer(RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .sslContext(sslContext)
                .requireClientCertificate(requireClientCertificate)
                .handshakeTimeout(Duration.ofSeconds(5))
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                        serverConnected.add(client);
                    }

                    @Override
                    public void onDisconnect(final Client client) {}

                    @Override
                    public void onMessage(final Client sender, final Message message) {
                        serverReceived.add(message);
                    }
                })
                .build());
        server.start();
    }

    private void connect(final String host, final SSLContext sslContext, final boolean verifyHostname) {
        client = new RavenClient(RavenClientConfiguration.builder()
                .host(host)
                .port(server.getPort())
                .sslContext(sslContext)
                .verifyHostname(verifyHostname)
                .connectTimeout(Duration.ofSeconds(5))
                .handler(new ClientHandler() {
                    @Override
                    public void onConnect() {}

                    @Override
                    public void onDisconnect() {}

                    @Override
                    public void onMessage(final Message message) {
                        clientReceived.add(message);
                    }
                })
                .build());
        client.connect();
    }
}
