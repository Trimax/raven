package io.github.trimax.raven.server;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.RavenClient;
import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.core.handler.ClientHandler;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Integration tests for {@link ServerMessageRouter} with a real Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServerMessageRouterTest.TestConfig.class)
class ServerMessageRouterTest {

    @Autowired
    private RavenServer ravenServer;

    @Autowired
    private TestHandler testHandler;

    @Test
    void messageDispatchesToAnnotatedMethod() {
        final var client = connectClient();

        client.send(new PingMessage("hello"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !testHandler.getReceivedPings().isEmpty());

        assertEquals("hello", testHandler.getReceivedPings().getFirst().getContent());
        client.disconnect();
    }

    @Test
    void connectDispatchesToAnnotatedMethod() {
        final var client = connectClient();

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !testHandler.getConnectedClients().isEmpty());

        assertFalse(testHandler.getConnectedClients().isEmpty());
        client.disconnect();
    }

    @Test
    void disconnectDispatchesToAnnotatedMethod() {
        final var client = connectClient();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !testHandler.getConnectedClients().isEmpty());

        client.disconnect();

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !testHandler.getDisconnectedClients().isEmpty());

        assertFalse(testHandler.getDisconnectedClients().isEmpty());
    }

    @Test
    void multipleHandlersForSameMessageType() {
        final var client = connectClient();

        client.send(new PingMessage("multi"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !testHandler.getReceivedPings().isEmpty()
                        && testHandler.getPingCount().get() >= 1);

        client.disconnect();
    }

    private RavenClient connectClient() {
        final var config = io.github.trimax.raven.core.config.RavenClientConfiguration.builder()
                .host("localhost")
                .port(ravenServer.getPort())
                .handler(new ClientHandler() {
                    @Override
                    public void onConnect() {}

                    @Override
                    public void onDisconnect() {}

                    @Override
                    public void onMessage(final Message message) {}
                })
                .build();
        final var client = new RavenClient(config);
        client.connect();
        await().atMost(2, TimeUnit.SECONDS).until(client::isConnected);
        return client;
    }

    // --- Test messages ---

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class PingMessage extends Message {
        private String content;
    }

    // --- Test handler ---

    @Component
    static class TestHandler {

        @Getter
        private final List<PingMessage> receivedPings = new CopyOnWriteArrayList<>();

        @Getter
        private final List<Client> connectedClients = new CopyOnWriteArrayList<>();

        @Getter
        private final List<Client> disconnectedClients = new CopyOnWriteArrayList<>();

        @Getter
        private final AtomicInteger pingCount = new AtomicInteger(0);

        @SubscribeMessage(PingMessage.class)
        public void onPing(final Client sender, final PingMessage message) {
            receivedPings.add(message);
            pingCount.incrementAndGet();
        }

        @SubscribeConnect
        public void onConnect(final Client client) {
            connectedClients.add(client);
        }

        @SubscribeDisconnect
        public void onDisconnect(final Client client) {
            disconnectedClients.add(client);
        }
    }

    // --- Config ---

    @Configuration
    @ComponentScan(excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class TestConfig {

        @Bean
        RavenServer ravenServer(final ServerMessageRouter router) {
            final var config = io.github.trimax.raven.core.config.RavenServerConfiguration.builder()
                    .port(0)
                    .handler(router)
                    .build();
            final var server = new RavenServer(config);
            server.start();
            return server;
        }
    }
}
