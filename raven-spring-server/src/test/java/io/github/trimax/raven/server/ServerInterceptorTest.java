package io.github.trimax.raven.server;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Integration tests verifying that {@link ServerMessageInterceptor} beans are
 * picked up by the autoconfiguration and applied before message dispatch.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServerInterceptorTest.TestConfig.class)
class ServerInterceptorTest {

    @Autowired
    private RavenServer ravenServer;

    @Autowired
    private TestHandler testHandler;

    @Autowired
    private BlockingInterceptor blockingInterceptor;

    @Test
    void interceptorBlocksMessage() {
        blockingInterceptor.setBlock(true);
        testHandler.getReceived().clear();

        final var client = connectClient();
        client.send(new PingMessage("should be blocked"));

        await().during(300, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> testHandler.getReceived().isEmpty());

        client.disconnect();
    }

    @Test
    void interceptorAllowsMessage() {
        blockingInterceptor.setBlock(false);
        testHandler.getReceived().clear();

        final var client = connectClient();
        client.send(new PingMessage("should pass"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !testHandler.getReceived().isEmpty());

        assertEquals("should pass", testHandler.getReceived().getFirst().getContent());
        client.disconnect();
    }

    @Test
    void interceptorReceivesCorrectMessageType() {
        blockingInterceptor.setBlock(false);
        blockingInterceptor.getIntercepted().clear();

        final var client = connectClient();
        client.send(new PingMessage("type-check"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !blockingInterceptor.getIntercepted().isEmpty());

        assertInstanceOf(PingMessage.class, blockingInterceptor.getIntercepted().getFirst());
        client.disconnect();
    }

    private RavenClient connectClient() {
        final var config = RavenClientConfiguration.builder()
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
    static class PingMessage extends Message {
        private String content;
    }

    // --- Interceptor ---

    @Component
    static class BlockingInterceptor implements ServerMessageInterceptor {

        @Getter
        private final List<Message> intercepted = new CopyOnWriteArrayList<>();

        @Setter
        private volatile boolean block;

        @Override
        public boolean preHandle(final Client sender, final Message message) {
            intercepted.add(message);
            return !block;
        }
    }

    // --- Handler ---

    @Component
    static class TestHandler {

        @Getter
        private final List<PingMessage> received = new CopyOnWriteArrayList<>();

        @SuppressWarnings("unused")
        @SubscribeMessage(PingMessage.class)
        public void onPing(final Client sender, final PingMessage message) {
            received.add(message);
        }
    }

    // --- Config ---

    @Configuration
    @ComponentScan(excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class TestConfig {

        @Bean
        RavenServer ravenServer(final ServerMessageRouter router, final BlockingInterceptor interceptor) {
            final var config = RavenServerConfiguration.builder()
                    .port(0)
                    .handler(router)
                    .interceptors(List.of(interceptor))
                    .build();
            final var server = new RavenServer(config);
            server.start();
            return server;
        }
    }
}
