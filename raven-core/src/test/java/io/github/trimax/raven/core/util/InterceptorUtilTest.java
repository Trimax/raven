package io.github.trimax.raven.core.util;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.RavenClient;
import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.interceptor.ClientMessageInterceptor;
import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;

/**
 * Unit tests for {@link InterceptorUtil}.
 */
class InterceptorUtilTest {

    private static RavenServer server;
    private static RavenClient ravenClient;
    private static final AtomicReference<Client> serverSideClient = new AtomicReference<>();

    @BeforeAll
    static void setUp() {
        final var serverConfig = RavenServerConfiguration.builder()
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) { serverSideClient.set(client); }

                    @Override
                    public void onDisconnect(final Client client) {}

                    @Override
                    public void onMessage(final Client sender, final Message message) {}
                })
                .build();
        server = new RavenServer(serverConfig);
        server.start();

        final var clientConfig = RavenClientConfiguration.builder()
                .host("localhost")
                .port(server.getPort())
                .handler(new NoOpClientHandler())
                .build();
        ravenClient = new RavenClient(clientConfig);
        ravenClient.connect();

        await().atMost(2, TimeUnit.SECONDS).until(() -> serverSideClient.get() != null);
    }

    @AfterAll
    static void tearDown() {
        ravenClient.disconnect();
        server.stop();
    }

    // --- Server interceptors ---

    @Test
    void serverInterceptors_emptyList_returnsTrue() {
        assertTrue(InterceptorUtil.shouldProceed(List.of(), client(), message()));
    }

    @Test
    void serverInterceptors_allPass_returnsTrue() {
        final var interceptors = List.<ServerMessageInterceptor>of(
                (_, _) -> true,
                (_, _) -> true
        );

        assertTrue(InterceptorUtil.shouldProceed(interceptors, client(), message()));
    }

    @Test
    void serverInterceptors_firstRejects_returnsFalse() {
        final var interceptors = List.<ServerMessageInterceptor>of(
                (_, _) -> false,
                (_, _) -> true
        );

        assertFalse(InterceptorUtil.shouldProceed(interceptors, client(), message()));
    }

    @Test
    void serverInterceptors_secondRejects_returnsFalse() {
        final var interceptors = List.<ServerMessageInterceptor>of(
                (_, _) -> true,
                (_, _) -> false
        );

        assertFalse(InterceptorUtil.shouldProceed(interceptors, client(), message()));
    }

    @Test
    void serverInterceptors_exceptionTreatedAsRejection() {
        final var interceptors = List.<ServerMessageInterceptor>of(
                (_, _) -> { throw new RuntimeException("boom"); }
        );

        assertFalse(InterceptorUtil.shouldProceed(interceptors, client(), message()));
    }

    @Test
    void serverInterceptors_exceptionStopsChain() {
        final var secondCalled = new boolean[]{false};
        final var interceptors = List.<ServerMessageInterceptor>of(
                (_, _) -> { throw new RuntimeException("fail"); },
                (_, _) -> { secondCalled[0] = true; return true; }
        );

        assertFalse(InterceptorUtil.shouldProceed(interceptors, client(), message()));
        assertFalse(secondCalled[0], "Second interceptor should not be called after first fails");
    }

    // --- Client interceptors ---

    @Test
    void clientInterceptors_emptyList_returnsTrue() {
        assertTrue(InterceptorUtil.shouldProceed(List.of(), message()));
    }

    @Test
    void clientInterceptors_allPass_returnsTrue() {
        final var interceptors = List.<ClientMessageInterceptor>of(
                _ -> true,
                _ -> true
        );

        assertTrue(InterceptorUtil.shouldProceed(interceptors, message()));
    }

    @Test
    void clientInterceptors_firstRejects_returnsFalse() {
        final var interceptors = List.<ClientMessageInterceptor>of(
                _ -> false,
                _ -> true
        );

        assertFalse(InterceptorUtil.shouldProceed(interceptors, message()));
    }

    @Test
    void clientInterceptors_secondRejects_returnsFalse() {
        final var interceptors = List.<ClientMessageInterceptor>of(
                _ -> true,
                _ -> false
        );

        assertFalse(InterceptorUtil.shouldProceed(interceptors, message()));
    }

    @Test
    void clientInterceptors_exceptionTreatedAsRejection() {
        final var interceptors = List.<ClientMessageInterceptor>of(
                _ -> { throw new RuntimeException("boom"); }
        );

        assertFalse(InterceptorUtil.shouldProceed(interceptors, message()));
    }

    @Test
    void clientInterceptors_exceptionStopsChain() {
        final var secondCalled = new boolean[]{false};
        final var interceptors = List.<ClientMessageInterceptor>of(
                _ -> { throw new RuntimeException("fail"); },
                _ -> { secondCalled[0] = true; return true; }
        );

        assertFalse(InterceptorUtil.shouldProceed(interceptors, message()));
        assertFalse(secondCalled[0], "Second interceptor should not be called after first fails");
    }

    // --- Helpers ---

    private static Client client() {
        return serverSideClient.get();
    }

    private static Message message() {
        return new TestMessage();
    }

    private static class TestMessage extends Message {
    }

    private static class NoOpClientHandler implements ClientHandler {
        @Override
        public void onConnect() {}

        @Override
        public void onDisconnect() {}

        @Override
        public void onMessage(final Message message) {}
    }
}
