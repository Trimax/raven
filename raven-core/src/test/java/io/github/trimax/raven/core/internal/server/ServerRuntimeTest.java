package io.github.trimax.raven.core.internal.server;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.RavenClient;
import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.core.RavenServerTestAccess;
import io.github.trimax.raven.core.TestMessage;
import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.internal.session.RawPeer;

/**
 * Unit tests for {@link ServerRuntime}: stop() completes once every session has ended, sessions it cuts short get no
 * callbacks, and a restarted server starts from scratch.
 */
class ServerRuntimeTest {

    private final List<String> events = new CopyOnWriteArrayList<>();

    @Test
    void stopReturnsAfterEveryOnDisconnect() throws Exception {
        final var runtime = start(new Recorder() {
            @Override
            public void onDisconnect(final Client client) {
                sleep(200);
                super.onDisconnect(client);
            }
        });
        final var clients = new ArrayList<RavenClient>();
        for (int i = 0; i < 3; i++) {
            final var client = client(runtime.port());
            client.connect();
            clients.add(client);
        }
        await().atMost(2, TimeUnit.SECONDS).until(() -> runtime.ids().size() == 3);

        runtime.stop().get(5, TimeUnit.SECONDS);

        assertEquals(3, events.stream().filter("disconnect"::equals).count());
        assertTrue(runtime.ids().isEmpty());
        // Each permit comes back once its session has ended, possibly just after stop() completed
        await().atMost(2, TimeUnit.SECONDS).until(() -> runtime.availableConnections() == 1024);
        clients.forEach(RavenClient::disconnect);
    }

    @Test
    void sessionInHandshakeDuringStopGetsNoCallbacks() throws Exception {
        final var runtime = start(new Recorder());
        try (final var peer = new Socket(InetAddress.getLoopbackAddress(), runtime.port())) {
            peer.setSoTimeout(5_000);
            RawPeer.readHello(peer.getInputStream());

            runtime.stop().get(2, TimeUnit.SECONDS);

            // Completing the handshake now must not announce the connection
            try {
                RawPeer.writeHello(peer.getOutputStream());
            } catch (final IOException _) {
                // the server may already have closed the socket
            }
            assertTrue(events.isEmpty());
            await().atMost(2, TimeUnit.SECONDS).until(() -> runtime.availableConnections() == 1024);
        }
    }

    @Test
    void stopDuringOnConnectReportsOneDisconnectAfterIt() throws Exception {
        final var inConnect = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var runtime = start(new Recorder() {
            @Override
            public void onConnect(final Client client) {
                super.onConnect(client);
                inConnect.countDown();
                awaitQuietly(release);
            }
        });
        final var client = client(runtime.port());
        CompletableFuture.runAsync(client::connect);
        assertTrue(inConnect.await(2, TimeUnit.SECONDS));

        final var stopping = runtime.stop();
        Thread.sleep(100);
        assertFalse(stopping.isDone(), "stop() must wait for the session it closed");
        release.countDown();

        stopping.get(2, TimeUnit.SECONDS);
        assertEquals(List.of("connect", "disconnect"), events);
        client.disconnect();
    }

    @Test
    void stopFromACallbackDoesNotWaitForItself() throws Exception {
        final var server = new AtomicReference<RavenServer>();
        final var stopped = new CompletableFuture<Void>();
        server.set(new RavenServer(configuration(new Recorder() {
            @Override
            public void onMessage(final Client sender, final Message message) {
                server.get().stop();
                stopped.complete(null);
            }
        })));
        server.get().start();
        final var client = client(server.get().getPort());
        client.connect();

        client.send(new TestMessage("stop"));

        stopped.get(2, TimeUnit.SECONDS);
        await().atMost(2, TimeUnit.SECONDS).until(() -> events.contains("disconnect"));
        client.disconnect();
    }

    @Test
    void restartedServerStartsFromScratch() {
        final var server = new RavenServer(configuration(new Recorder()));
        server.start();
        final var first = client(server.getPort());
        first.connect();
        await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 1);

        server.stop();
        assertTrue(server.getClients().isEmpty());
        server.start();

        try {
            final var second = client(server.getPort());
            second.connect();
            await().atMost(2, TimeUnit.SECONDS).until(() -> server.getClients().size() == 1);
            assertEquals(List.of("connect", "disconnect", "connect"), events);
            assertEquals(1023, RavenServerTestAccess.availableConnections(server));
            second.disconnect();
        } finally {
            first.disconnect();
            server.stop();
        }
    }

    private ServerRuntime start(final ServerHandler handler) throws IOException {
        final var runtime = ServerRuntime.bind(configuration(handler));
        runtime.start();
        return runtime;
    }

    private static RavenServerConfiguration configuration(final ServerHandler handler) {
        return RavenServerConfiguration.builder()
                .bindAddress(InetAddress.getLoopbackAddress())
                .port(0)
                .handshakeTimeout(Duration.ofSeconds(30))
                .handler(handler)
                .build();
    }

    private static RavenClient client(final int port) {
        return new RavenClient(RavenClientConfiguration.builder()
                .host(InetAddress.getLoopbackAddress().getHostAddress())
                .port(port)
                .handler(new ClientHandler() {
                    @Override
                    public void onConnect() {
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

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Records the connect and disconnect callbacks.
     */
    private class Recorder implements ServerHandler {
        @Override
        public void onConnect(final Client client) {
            events.add("connect");
        }

        @Override
        public void onDisconnect(final Client client) {
            events.add("disconnect");
        }

        @Override
        public void onMessage(final Client sender, final Message message) {
        }
    }
}
