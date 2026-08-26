package io.github.trimax.raven.server;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Tests that when a handler throws an exception, other handlers for the same message type
 * are still invoked (fault isolation).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServerMessageRouterExceptionTest.TestConfig.class)
class ServerMessageRouterExceptionTest {

    @Autowired
    private RavenServer ravenServer;

    @Autowired
    private ThrowingHandler throwingHandler;

    @Autowired
    private SecondHandler secondHandler;

    @Test
    void handlerExceptionDoesNotPreventOtherHandlers() {
        final var client = connectClient();

        client.send(new FaultMessage("trigger"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !secondHandler.getReceived().isEmpty());

        // The second handler should still receive the message even though the first one throws an exception
        assertEquals(1, secondHandler.getReceived().size());
        assertEquals("trigger", secondHandler.getReceived().getFirst().getContent());

        // The throwing handler was also invoked (it incremented before throwing)
        assertTrue(throwingHandler.getInvocationCount().get() > 0);

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

    // --- Message ---

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class FaultMessage extends Message {
        private String content;
    }

    // --- Handlers ---

    @Component
    static class ThrowingHandler {

        @Getter
        private final AtomicInteger invocationCount = new AtomicInteger(0);

        @SuppressWarnings("unused")
        @SubscribeMessage(FaultMessage.class)
        public void onFault(final Client sender, final FaultMessage message) {
            invocationCount.incrementAndGet();
            throw new RuntimeException("Intentional test failure");
        }
    }

    @Component
    static class SecondHandler {

        @Getter
        private final List<FaultMessage> received = new CopyOnWriteArrayList<>();

        @SuppressWarnings("unused")
        @SubscribeMessage(FaultMessage.class)
        public void onFault(final Client sender, final FaultMessage message) {
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
