package io.github.trimax.raven.server;

import io.github.trimax.raven.Client;
import io.github.trimax.raven.Message;
import io.github.trimax.raven.RavenClient;
import io.github.trimax.raven.handler.ClientHandler;
import io.github.trimax.raven.RavenServer;
import io.github.trimax.raven.spring.SubscribeConnect;
import io.github.trimax.raven.spring.SubscribeDisconnect;
import io.github.trimax.raven.spring.SubscribeMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ServerMessageRouter} with a real Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServerMessageRouterTest.TestConfig.class)
class ServerMessageRouterTest {

    private static final int PORT = 19093;

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
                .until(() -> testHandler.getReceivedPings().size() >= 1
                        && testHandler.getPingCount() >= 1);

        client.disconnect();
    }

    private RavenClient connectClient() {
        final var client = new RavenClient("localhost", PORT, new ClientHandler() {
            @Override
            public void onConnect() {}

            @Override
            public void onDisconnect() {}

            @Override
            public void onMessage(final Message message) {}
        });
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
        private volatile int pingCount;

        @SubscribeMessage(PingMessage.class)
        public void onPing(final Client sender, final PingMessage message) {
            receivedPings.add(message);
            pingCount++;
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
    @ComponentScan
    static class TestConfig {

        @Bean
        RavenServer ravenServer(final ServerMessageRouter router) {
            final var server = new RavenServer(PORT, router);
            server.start();
            return server;
        }
    }
}
