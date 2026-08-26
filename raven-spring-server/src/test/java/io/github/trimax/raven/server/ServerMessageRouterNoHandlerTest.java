package io.github.trimax.raven.server;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
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
 * Tests that when no handler is registered for a message type, the message is silently dropped
 * without errors, and the server continues to function normally for further messages.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServerMessageRouterNoHandlerTest.TestConfig.class)
class ServerMessageRouterNoHandlerTest {

    @Autowired
    private RavenServer ravenServer;

    @Autowired
    private KnownHandler knownHandler;

    @Test
    void unhandledMessageIsSilentlyDropped() {
        final var client = connectClient();

        // Send a message with no registered handler — should not crash
        client.send(new UnknownMessage("nobody listens"));

        // Then send a known message to prove the server is still functional
        client.send(new KnownMessage("still alive"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !knownHandler.getReceived().isEmpty());

        assertTrue(knownHandler.getReceived().stream()
                .anyMatch(m -> "still alive".equals(m.getContent())));

        client.disconnect();
    }

    @Test
    void multipleUnhandledMessagesDoNotCrashServer() {
        final var client = connectClient();

        for (int i = 0; i < 5; i++) {
            client.send(new UnknownMessage("unhandled-" + i));
        }

        client.send(new KnownMessage("after-unhandled"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> knownHandler.getReceived().stream()
                        .anyMatch(m -> "after-unhandled".equals(m.getContent())));

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

    // --- Messages ---

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class UnknownMessage extends Message {
        private String content;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class KnownMessage extends Message {
        private String content;
    }

    // --- Handler only for KnownMessage ---

    @Component
    static class KnownHandler {

        @Getter
        private final List<KnownMessage> received = new CopyOnWriteArrayList<>();

        @SuppressWarnings("unused")
        @SubscribeMessage(KnownMessage.class)
        public void onKnown(final Client sender, final KnownMessage message) {
            received.add(message);
        }
    }

    // --- Config ---

    @Configuration
    static class TestConfig {

        @Bean
        ServerMessageRouter serverMessageRouter() {
            return new ServerMessageRouter();
        }

        @Bean
        KnownHandler knownHandler() {
            return new KnownHandler();
        }

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
