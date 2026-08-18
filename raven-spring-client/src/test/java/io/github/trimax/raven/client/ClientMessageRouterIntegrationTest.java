package io.github.trimax.raven.client;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.RavenClient;
import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.Client;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ClientMessageRouter} with a real Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ClientMessageRouterIntegrationTest.TestConfig.class)
class ClientMessageRouterIntegrationTest {

    private static RavenServer server;

    @Autowired
    private RavenClient ravenClient;

    @Autowired
    private TestHandler testHandler;

    @BeforeAll
    static void startServer() {
        server = new RavenServer(0, new ServerHandler() {
            @Override
            public void onConnect(final Client client) {}

            @Override
            public void onDisconnect(final Client client) {}

            @Override
            public void onMessage(final Client sender, final Message message) {
                // Echo back to client
                server.send(new PongMessage("pong"), sender.getId());
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void messageDispatchesToAnnotatedMethod() {
        await().atMost(2, TimeUnit.SECONDS).until(ravenClient::isConnected);

        // Server sends a message to client
        server.send(new PongMessage("hello client"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !testHandler.getReceivedMessages().isEmpty());

        assertEquals("hello client", testHandler.getReceivedMessages().getFirst().getContent());
    }

    @Test
    void connectDispatchesToAnnotatedMethod() {
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> testHandler.getConnectCount().get() > 0);

        assertTrue(testHandler.getConnectCount().get() > 0);
    }

    @Test
    void disconnectDispatchesToAnnotatedMethod() {
        await().atMost(2, TimeUnit.SECONDS).until(ravenClient::isConnected);

        ravenClient.disconnect();

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> testHandler.getDisconnectCount().get() > 0);

        assertTrue(testHandler.getDisconnectCount().get() > 0);

        // Reconnect for other tests
        ravenClient.connect();
        await().atMost(2, TimeUnit.SECONDS).until(ravenClient::isConnected);
    }

    // --- Test messages ---

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class PongMessage extends Message {
        private String content;
    }

    // --- Test handler ---

    @Component
    static class TestHandler {

        @Getter
        private final List<PongMessage> receivedMessages = new CopyOnWriteArrayList<>();

        @Getter
        private final AtomicInteger connectCount = new AtomicInteger(0);

        @Getter
        private final AtomicInteger disconnectCount = new AtomicInteger(0);

        @SubscribeMessage(PongMessage.class)
        public void onPong(final PongMessage message) {
            receivedMessages.add(message);
        }

        @SubscribeConnect
        public void onConnect() {
            connectCount.incrementAndGet();
        }

        @SubscribeDisconnect
        public void onDisconnect() {
            disconnectCount.incrementAndGet();
        }
    }

    // --- Config ---

    @Configuration
    @ComponentScan(excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class TestConfig {

        @Bean
        RavenClient ravenClient(final ClientMessageRouter router) {
            final var client = new RavenClient("localhost", server.getPort(), router);
            client.connect();
            return client;
        }
    }
}
