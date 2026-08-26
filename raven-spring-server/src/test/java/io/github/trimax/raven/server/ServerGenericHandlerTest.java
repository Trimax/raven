package io.github.trimax.raven.server;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

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
 * Tests that a server-side catch-all handler ({@code @SubscribeMessage(Message.class)}) receives
 * all message types, and that a specific handler is not invoked twice when both exist.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServerGenericHandlerTest.TestConfig.class)
class ServerGenericHandlerTest {

    @Autowired
    private RavenServer ravenServer;

    @Autowired
    private CatchAllHandler catchAllHandler;

    @Autowired
    private SpecificHandler specificHandler;

    @Test
    void catchAllReceivesAllMessageTypes() {
        catchAllHandler.getReceived().clear();

        final var client = connectClient();

        client.send(new AlphaMessage("a1"));
        client.send(new BetaMessage("b1"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> catchAllHandler.getReceived().size() >= 2);

        assertEquals(2, catchAllHandler.getReceived().size());
        client.disconnect();
    }

    @Test
    void specificHandlerCalledOnceNotDuplicated() {
        catchAllHandler.getReceived().clear();
        specificHandler.getReceived().clear();

        final var client = connectClient();

        client.send(new AlphaMessage("test"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !specificHandler.getReceived().isEmpty());

        // Specific handler called exactly once
        assertEquals(1, specificHandler.getReceived().size());
        assertEquals("test", specificHandler.getReceived().getFirst().getContent());

        // Catch-all also called exactly once for the same message
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !catchAllHandler.getReceived().isEmpty());
        assertEquals(1, catchAllHandler.getReceived().size());

        client.disconnect();
    }

    @Test
    void catchAllReceivesMessageWithNoSpecificHandler() {
        catchAllHandler.getReceived().clear();

        final var client = connectClient();

        client.send(new BetaMessage("unhandled-specific"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !catchAllHandler.getReceived().isEmpty());

        assertEquals(1, catchAllHandler.getReceived().size());
        client.disconnect();
    }

    private RavenClient connectClient() {
        final var client = new RavenClient("localhost", ravenServer.getPort(), new ClientHandler() {
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

    // --- Messages ---

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class AlphaMessage extends Message {
        private String content;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class BetaMessage extends Message {
        private String content;
    }

    // --- Handlers ---

    @Component
    static class CatchAllHandler {

        @Getter
        private final List<Message> received = new CopyOnWriteArrayList<>();

        @SuppressWarnings("unused")
        @SubscribeMessage(Message.class)
        public void onAny(final Client sender, final Message message) {
            received.add(message);
        }
    }

    @Component
    static class SpecificHandler {

        @Getter
        private final List<AlphaMessage> received = new CopyOnWriteArrayList<>();

        @SuppressWarnings("unused")
        @SubscribeMessage(AlphaMessage.class)
        public void onAlpha(final Client sender, final AlphaMessage message) {
            received.add(message);
        }
    }

    // --- Config ---

    @Configuration
    @ComponentScan(excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class TestConfig {

        @Bean
        RavenServer ravenServer(final ServerMessageRouter router) {
            final var server = new RavenServer(0, router);
            server.start();
            return server;
        }
    }
}
