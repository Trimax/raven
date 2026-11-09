package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies the defaults and the validation of {@code sendQueueCapacity} and {@code sendTimeout} on both configurations.
 */
class SendQueueConfigTest {

    private static final ServerHandler SERVER_NOOP = new ServerHandler() {
        @Override
        public void onConnect(final Client client) {
        }

        @Override
        public void onDisconnect(final Client client) {
        }

        @Override
        public void onMessage(final Client sender, final Message message) {
        }
    };

    private static final ClientHandler CLIENT_NOOP = new ClientHandler() {
        @Override
        public void onConnect() {
        }

        @Override
        public void onDisconnect() {
        }

        @Override
        public void onMessage(final Message message) {
        }
    };

    @Test
    void defaultsTo1024() {
        assertEquals(1024, RavenServerConfiguration.builder().port(0).handler(SERVER_NOOP).build().getSendQueueCapacity());
        assertEquals(1024, RavenClientConfiguration.builder().host("localhost").port(0).handler(CLIENT_NOOP).build().getSendQueueCapacity());
    }

    @Test
    void serverAcceptsCapacityOfOne() {
        final var configuration = RavenServerConfiguration.builder().port(0).handler(SERVER_NOOP).sendQueueCapacity(1).build();

        assertDoesNotThrow(() -> new RavenServer(configuration));
    }

    @Test
    void serverRejectsZeroCapacity() {
        final var configuration = RavenServerConfiguration.builder().port(0).handler(SERVER_NOOP).sendQueueCapacity(0).build();

        assertThrows(IllegalArgumentException.class, () -> new RavenServer(configuration));
    }

    @Test
    void clientAcceptsCapacityOfOne() {
        final var configuration = RavenClientConfiguration.builder().host("localhost").port(0).handler(CLIENT_NOOP).sendQueueCapacity(1).build();

        assertDoesNotThrow(() -> new RavenClient(configuration));
    }

    @Test
    void clientRejectsNegativeCapacity() {
        final var configuration = RavenClientConfiguration.builder().host("localhost").port(0).handler(CLIENT_NOOP).sendQueueCapacity(-1).build();

        assertThrows(IllegalArgumentException.class, () -> new RavenClient(configuration));
    }

    @Test
    void sendTimeoutDefaultsToFiveSeconds() {
        assertEquals(Duration.ofSeconds(5), RavenServerConfiguration.builder().port(0).handler(SERVER_NOOP).build().getSendTimeout());
        assertEquals(Duration.ofSeconds(5), RavenClientConfiguration.builder().host("localhost").port(0).handler(CLIENT_NOOP).build().getSendTimeout());
    }

    @Test
    void acceptsZeroSendTimeout() {
        final var server = RavenServerConfiguration.builder().port(0).handler(SERVER_NOOP).sendTimeout(Duration.ZERO).build();
        final var client = RavenClientConfiguration.builder().host("localhost").port(0).handler(CLIENT_NOOP).sendTimeout(Duration.ZERO).build();

        assertDoesNotThrow(() -> new RavenServer(server));
        assertDoesNotThrow(() -> new RavenClient(client));
    }

    @Test
    void rejectsNegativeSendTimeout() {
        final var server = RavenServerConfiguration.builder().port(0).handler(SERVER_NOOP).sendTimeout(Duration.ofMillis(-1)).build();
        final var client = RavenClientConfiguration.builder().host("localhost").port(0).handler(CLIENT_NOOP).sendTimeout(Duration.ofMillis(-1)).build();

        assertThrows(IllegalArgumentException.class, () -> new RavenServer(server));
        assertThrows(IllegalArgumentException.class, () -> new RavenClient(client));
    }
}
