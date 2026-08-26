package io.github.trimax.raven.client;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

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

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.RavenClient;
import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.core.handler.ServerHandler;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tests that a catch-all handler ({@code @SubscribeMessage(Message.class)}) receives all messages,
 * and that a specific handler is not invoked twice when both catch-all and specific handlers exist.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = GenericHandlerTest.TestConfig.class)
class GenericHandlerTest {

    private static RavenServer server;

    @Autowired
    private RavenClient ravenClient;

    @Autowired
    private CatchAllHandler catchAllHandler;

    @Autowired
    private SpecificHandler specificHandler;

    @BeforeAll
    static void startServer() {
        server = new RavenServer(0, new ServerHandler() {
            @Override
            public void onConnect(final Client client) {
            }

            @Override
            public void onDisconnect(final Client client) {
            }

            @Override
            public void onMessage(final Client sender, final Message message) {
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void catchAllReceivesAllMessages() {
        await().atMost(2, TimeUnit.SECONDS).until(ravenClient::isConnected);

        server.broadcast(new PingMessage("ping1"));
        server.broadcast(new PongMessage("pong1"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> catchAllHandler.getReceived().size() >= 2);

        assertEquals(2, catchAllHandler.getReceived().size());
    }

    @Test
    void specificHandlerCalledOnceNotTwice() {
        await().atMost(2, TimeUnit.SECONDS).until(ravenClient::isConnected);

        catchAllHandler.getReceived().clear();
        specificHandler.getReceived().clear();

        server.broadcast(new PingMessage("test"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !specificHandler.getReceived().isEmpty());

        // Specific handler called exactly once
        assertEquals(1, specificHandler.getReceived().size());
        assertEquals("test", specificHandler.getReceived().getFirst().getContent());

        // Catch-all also called exactly once for the same message
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !catchAllHandler.getReceived().isEmpty());
        assertEquals(1, catchAllHandler.getReceived().size());
    }

    // --- Messages ---

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class PingMessage extends Message {
        private String content;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class PongMessage extends Message {
        private String content;
    }

    // --- Handlers ---

    @Component
    static class CatchAllHandler {

        @Getter
        private final List<Message> received = new CopyOnWriteArrayList<>();

        @SuppressWarnings("unused")
        @SubscribeMessage(Message.class)
        public void onAny(final Message message) {
            received.add(message);
        }
    }

    @Component
    static class SpecificHandler {

        @Getter
        private final List<PingMessage> received = new CopyOnWriteArrayList<>();

        @SuppressWarnings("unused")
        @SubscribeMessage(PingMessage.class)
        public void onPing(final PingMessage message) {
            received.add(message);
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
