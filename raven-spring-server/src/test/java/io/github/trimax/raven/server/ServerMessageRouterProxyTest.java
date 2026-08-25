package io.github.trimax.raven.server;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.RavenClient;
import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.core.handler.ClientHandler;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Integration test verifying that {@link ServerMessageRouter} correctly discovers
 * {@code @SubscribeMessage} methods on both plain beans and CGLIB-proxied beans
 * (e.g., those with {@code @Transactional}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ServerMessageRouterProxyTest.TestConfig.class)
class ServerMessageRouterProxyTest {

    @Autowired
    private RavenServer ravenServer;

    @Autowired
    private PlainHandler plainHandler;

    @Autowired
    private TransactionalHandler transactionalHandler;

    @Test
    void plainBeanHandlerReceivesMessages() {
        final var client = connectClient();

        client.send(new PingMessage("plain"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !plainHandler.getReceived().isEmpty());

        assertEquals("plain", plainHandler.getReceived().getFirst().getContent());
        client.disconnect();
    }

    @Test
    void cglibProxiedBeanHandlerReceivesMessages() {
        final var client = connectClient();

        client.send(new PongMessage("proxied"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !transactionalHandler.getReceived().isEmpty());

        assertEquals("proxied", transactionalHandler.getReceived().getFirst().getContent());
        client.disconnect();
    }

    @Test
    void bothHandlersCoexist() {
        final var client = connectClient();
        final var plainBefore = plainHandler.getReceived().size();
        final var proxiedBefore = transactionalHandler.getReceived().size();

        client.send(new PingMessage("one"));
        client.send(new PongMessage("two"));

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> plainHandler.getReceived().size() > plainBefore
                        && transactionalHandler.getReceived().size() > proxiedBefore);

        assertEquals("one", plainHandler.getReceived().getLast().getContent());
        assertEquals("two", transactionalHandler.getReceived().getLast().getContent());
        client.disconnect();
    }

    @Test
    void connectAndDisconnectWorkOnProxiedBean() {
        final var client = connectClient();

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !transactionalHandler.getConnected().isEmpty());

        client.disconnect();

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !transactionalHandler.getDisconnected().isEmpty());

        assertFalse(transactionalHandler.getConnected().isEmpty());
        assertFalse(transactionalHandler.getDisconnected().isEmpty());
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
    static class PingMessage extends Message {
        private String content;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class PongMessage extends Message {
        private String content;
    }

    // --- Plain handler (no proxy) ---

    @Component
    static final class PlainHandler {

        @Getter
        private final List<PingMessage> received = new CopyOnWriteArrayList<>();

        @SuppressWarnings("unused")
        @SubscribeMessage(PingMessage.class)
        public void onPing(final Client ignored, final PingMessage message) {
            received.add(message);
        }
    }

    // --- Transactional handler (CGLIB proxy due to @Transactional) ---

    @Component
    static class TransactionalHandler {

        @Getter
        private final List<PongMessage> received = new CopyOnWriteArrayList<>();

        @Getter
        private final List<Client> connected = new CopyOnWriteArrayList<>();

        @Getter
        private final List<Client> disconnected = new CopyOnWriteArrayList<>();

        @Transactional
        @SuppressWarnings("unused")
        @SubscribeMessage(PongMessage.class)
        public void onPong(final Client ignored, final PongMessage message) {
            received.add(message);
        }

        @SubscribeConnect
        public void onConnect(final Client client) {
            connected.add(client);
        }

        @SubscribeDisconnect
        @SuppressWarnings("unused")
        public void onDisconnect(final Client client) {
            disconnected.add(client);
        }
    }

    // --- Config ---

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        ServerMessageRouter serverMessageRouter() {
            return new ServerMessageRouter();
        }

        @Bean
        RavenServer ravenServer(final ServerMessageRouter router) {
            final var server = new RavenServer(0, router);
            server.start();
            return server;
        }

        @Bean
        PlainHandler plainHandler() {
            return new PlainHandler();
        }

        @Bean
        TransactionalHandler transactionalHandler() {
            return new TransactionalHandler();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }
    }

    /**
     * No-op transaction manager that does nothing — only used to trigger CGLIB proxying.
     */
    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @NonNull
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(@NonNull final Object transaction, @NonNull final org.springframework.transaction.TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(@NonNull final DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(@NonNull final DefaultTransactionStatus status) {
        }
    }
}
