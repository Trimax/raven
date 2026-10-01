package io.github.trimax.raven.client;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.Serial;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import io.github.trimax.raven.core.validation.annotation.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * End-to-end tests for client-side dispatch along the message superclass chain.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ClientHierarchyHandlerTest.TestConfig.class)
class ClientHierarchyHandlerTest {

    private static RavenServer server;

    @Autowired
    private RavenClient ravenClient;

    @Autowired
    private CallLog callLog;

    @BeforeAll
    static void startServer() {
        final var config = RavenServerConfiguration.builder()
                .port(0)
                .handler(new ServerHandler() {
                    @Override
                    public void onConnect(final Client client) {
                    }

                    @Override
                    public void onDisconnect(final Client client) {
                    }

                    @Override
                    public void onMessage(final Client sender, final Message message) {
                    }
                })
                .build();
        server = new RavenServer(config);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void setUp() {
        await().atMost(2, TimeUnit.SECONDS).until(ravenClient::isConnected);
        callLog.getCalls().clear();
        callLog.getMessages().clear();
    }

    @Test
    void handlersInvokedFromSpecificToBaseToGeneric() {
        server.broadcast(UpdateRequest.builder().requestId("req-1").version(7).build());

        await().atMost(2, TimeUnit.SECONDS).until(() -> callLog.getCalls().size() >= 3);

        assertEquals(List.of("update:UpdateRequest", "base:UpdateRequest", "all:UpdateRequest"), callLog.getCalls());

        final var received = assertInstanceOf(UpdateRequest.class, callLog.getMessages().getFirst());
        assertEquals("req-1", received.getRequestId());
        assertEquals(7, received.getVersion());
    }

    @Test
    void siblingSubclassReachesOnlyBaseAndGenericHandlers() {
        server.broadcast(DeleteRequest.builder().requestId("req-1").build());

        await().atMost(2, TimeUnit.SECONDS).until(() -> callLog.getCalls().size() >= 2);

        assertEquals(List.of("base:DeleteRequest", "all:DeleteRequest"), callLog.getCalls());
    }

    // --- Messages ---

    @Getter
    @SuperBuilder(toBuilder = true)
    abstract static class BaseRequest extends Message {

        @Serial
        private static final long serialVersionUID = 1L;

        @NotBlank
        private final String requestId;
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    static final class UpdateRequest extends BaseRequest {

        @Serial
        private static final long serialVersionUID = 1L;

        private final int version;
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    static final class DeleteRequest extends BaseRequest {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    // --- Handlers ---

    @Getter
    @Component
    static class CallLog {
        private final List<String> calls = new CopyOnWriteArrayList<>();
        private final List<Message> messages = new CopyOnWriteArrayList<>();

        void record(final String handler, final Message message) {
            calls.add(handler + ":" + message.getClass().getSimpleName());
            messages.add(message);
        }
    }

    @Component
    @RequiredArgsConstructor
    static class HierarchyCatchAllHandler {
        private final CallLog callLog;

        @SuppressWarnings("unused")
        @SubscribeMessage(Message.class)
        public void onAny(final Message message) {
            callLog.record("all", message);
        }
    }

    @Component
    @RequiredArgsConstructor
    static class BaseRequestHandler {
        private final CallLog callLog;

        @SuppressWarnings("unused")
        @SubscribeMessage(BaseRequest.class)
        public void onBase(final BaseRequest message) {
            callLog.record("base", message);
        }
    }

    @Component
    @RequiredArgsConstructor
    static class UpdateRequestHandler {
        private final CallLog callLog;

        @SuppressWarnings("unused")
        @SubscribeMessage(UpdateRequest.class)
        public void onUpdate(final UpdateRequest message) {
            callLog.record("update", message);
        }
    }

    // --- Config ---

    @Configuration
    @ComponentScan(excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class TestConfig {

        @Bean
        RavenClient ravenClient(final ClientMessageRouter router) {
            final var config = RavenClientConfiguration.builder()
                    .host("localhost")
                    .port(server.getPort())
                    .handler(router)
                    .build();
            final var client = new RavenClient(config);
            client.connect();
            return client;
        }
    }
}
