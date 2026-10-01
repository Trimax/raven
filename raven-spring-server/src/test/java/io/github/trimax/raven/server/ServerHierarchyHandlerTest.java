package io.github.trimax.raven.server;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.Serial;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
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
import io.github.trimax.raven.core.exception.MessageValidationRavenException;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.validation.annotation.NotBlank;
import io.github.trimax.raven.core.validation.annotation.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * End-to-end tests for server-side dispatch along the message superclass chain:
 * handlers on an abstract base class, ordering from specific to generic, and sibling isolation.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServerHierarchyHandlerTest.TestConfig.class)
class ServerHierarchyHandlerTest {

    @Autowired
    private RavenServer ravenServer;

    @Autowired
    private CallLog callLog;

    private RavenClient client;

    @BeforeEach
    void setUp() {
        callLog.getCalls().clear();
        callLog.getMessages().clear();
        client = connectClient();
    }

    @AfterEach
    void tearDown() {
        client.disconnect();
    }

    @Test
    void handlersInvokedFromSpecificToBaseToGeneric() {
        final var userId = UUID.randomUUID();
        client.send(CreateRequest.builder().requestId("req-1").userId(userId).name("alpha").build());

        await().atMost(2, TimeUnit.SECONDS).until(() -> callLog.getCalls().size() >= 3);

        assertEquals(List.of("create:CreateRequest", "base:CreateRequest", "all:CreateRequest"), callLog.getCalls());

        // Fields of the abstract superclass and of the concrete class survive the network round-trip
        final var received = assertInstanceOf(CreateRequest.class, callLog.getMessages().getFirst());
        assertEquals("req-1", received.getRequestId());
        assertEquals(userId, received.getUserId());
        assertEquals("alpha", received.getName());
    }

    @Test
    void baseHandlerReceivesSiblingSubclassWithoutSpecificHandler() {
        client.send(DeleteRequest.builder().requestId("req-1").userId(UUID.randomUUID()).build());

        await().atMost(2, TimeUnit.SECONDS).until(() -> callLog.getCalls().size() >= 2);

        // CreateRequest handler must not receive its sibling
        assertEquals(List.of("base:DeleteRequest", "all:DeleteRequest"), callLog.getCalls());
    }

    @Test
    void unrelatedMessageReachesOnlyGenericHandler() {
        client.send(PlainMessage.builder().text("hi").build());

        await().atMost(2, TimeUnit.SECONDS).until(() -> !callLog.getCalls().isEmpty());

        assertEquals(List.of("all:PlainMessage"), callLog.getCalls());
    }

    @Test
    void validationRejectsInvalidSuperclassField() {
        final var message = DeleteRequest.builder().requestId(" ").userId(UUID.randomUUID()).build();

        final var ex = assertThrows(MessageValidationRavenException.class, () -> client.send(message));
        assertEquals("requestId", ex.getViolations().getFirst().fieldName());
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
        final var ravenClient = new RavenClient(config);
        ravenClient.connect();
        await().atMost(2, TimeUnit.SECONDS).until(ravenClient::isConnected);
        return ravenClient;
    }

    // --- Messages ---

    @Getter
    @SuperBuilder(toBuilder = true)
    abstract static class BaseRequest extends Message {

        @Serial
        private static final long serialVersionUID = 1L;

        @NotBlank
        private final String requestId;

        @NotNull
        private final UUID userId;
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    static final class CreateRequest extends BaseRequest {

        @Serial
        private static final long serialVersionUID = 1L;

        @NotBlank
        private final String name;
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    static final class DeleteRequest extends BaseRequest {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    static final class PlainMessage extends Message {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String text;
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
        public void onAny(final Client sender, final Message message) {
            callLog.record("all", message);
        }
    }

    @Component
    @RequiredArgsConstructor
    static class BaseRequestHandler {
        private final CallLog callLog;

        @SuppressWarnings("unused")
        @SubscribeMessage(BaseRequest.class)
        public void onBase(final Client sender, final BaseRequest message) {
            callLog.record("base", message);
        }
    }

    @Component
    @RequiredArgsConstructor
    static class CreateRequestHandler {
        private final CallLog callLog;

        @SuppressWarnings("unused")
        @SubscribeMessage(CreateRequest.class)
        public void onCreate(final Client sender, final CreateRequest message) {
            callLog.record("create", message);
        }
    }

    // --- Config ---

    @Configuration
    @ComponentScan(excludeFilters = @ComponentScan.Filter(Configuration.class))
    static class TestConfig {

        @Bean
        RavenServer ravenServer(final ServerMessageRouter router) {
            final var config = RavenServerConfiguration.builder()
                    .port(0)
                    .handler(router)
                    .build();
            final var server = new RavenServer(config);
            server.start();
            return server;
        }
    }
}
