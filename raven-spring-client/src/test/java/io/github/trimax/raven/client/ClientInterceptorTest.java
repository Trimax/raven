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
import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.interceptor.ClientMessageInterceptor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Integration tests verifying that {@link ClientMessageInterceptor} beans are
 * picked up by the configuration and applied before message dispatch on the client.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ClientInterceptorTest.TestConfig.class)
class ClientInterceptorTest {

    private static RavenServer server;

    @Autowired
    private RavenClient ravenClient;

    @Autowired
    private TestHandler testHandler;

    @Autowired
    private BlockingInterceptor blockingInterceptor;

    @BeforeAll
    static void startServer() {
        final var config = RavenServerConfiguration.builder()
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {}

                    @Override
                    public void onDisconnect(final Client client) {}

                    @Override
                    public void onMessage(final Client sender, final Message message) {}
                })
                .build();
        server = new RavenServer(config);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void interceptorBlocksMessage() {
        await().atMost(2, TimeUnit.SECONDS).until(ravenClient::isConnected);
        blockingInterceptor.setBlock(true);
        testHandler.getReceived().clear();

        // Server broadcasts to client — interceptor should block
        server.broadcast(new PongMessage("should be blocked"));

        await().during(300, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .until(() -> testHandler.getReceived().isEmpty());
    }

    @Test
    void interceptorAllowsMessage() {
        await().atMost(2, TimeUnit.SECONDS).until(ravenClient::isConnected);
        blockingInterceptor.setBlock(false);
        testHandler.getReceived().clear();

        server.broadcast(new PongMessage("should pass"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !testHandler.getReceived().isEmpty());

        assertEquals("should pass", testHandler.getReceived().getFirst().getContent());
    }

    // --- Messages ---

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class PongMessage extends Message {
        private String content;
    }

    // --- Interceptor ---

    @Component
    static class BlockingInterceptor implements ClientMessageInterceptor {

        @Getter
        private final List<Message> intercepted = new CopyOnWriteArrayList<>();

        @Setter
        private volatile boolean block;

        @Override
        public boolean intercept(final Message message) {
            intercepted.add(message);
            return !block;
        }
    }

    // --- Handler ---

    @Component
    static class TestHandler {

        @Getter
        private final List<PongMessage> received = new CopyOnWriteArrayList<>();

        @SuppressWarnings("unused")
        @SubscribeMessage(PongMessage.class)
        public void onPong(final PongMessage message) {
            received.add(message);
        }
    }

    // --- Config ---

    @Configuration
    @ComponentScan(excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class TestConfig {

        @Bean
        RavenClient ravenClient(final ClientMessageRouter router, final BlockingInterceptor interceptor) {
            final var config = RavenClientConfiguration.builder()
                    .host("localhost")
                    .port(server.getPort())
                    .handler(router)
                    .interceptors(List.of(interceptor))
                    .build();
            final var client = new RavenClient(config);
            client.connect();
            return client;
        }
    }
}
