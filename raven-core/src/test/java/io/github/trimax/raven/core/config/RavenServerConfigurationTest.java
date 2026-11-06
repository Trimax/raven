package io.github.trimax.raven.core.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies {@link RavenServerConfiguration#validate()} directly, without starting a server.
 */
class RavenServerConfigurationTest {

    private static final ServerHandler NOOP = new ServerHandler() {
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

    @Test
    void defaultsAreValid() {
        assertDoesNotThrow(() -> configuration(Duration.ofSeconds(5)).validate());
    }

    @Test
    void listensOnAllAddressesByDefault() {
        assertNull(configuration(Duration.ofSeconds(5)).getBindAddress());
    }

    @Test
    void acceptsOneMillisecond() {
        assertDoesNotThrow(() -> configuration(Duration.ofMillis(1)).validate());
    }

    @Test
    void rejectsZeroHandshakeTimeout() {
        assertThrows(IllegalArgumentException.class, () -> configuration(Duration.ZERO).validate());
    }

    @Test
    void rejectsNegativeHandshakeTimeout() {
        assertThrows(IllegalArgumentException.class, () -> configuration(Duration.ofMillis(-1)).validate());
    }

    @Test
    void rejectsHandshakeTimeoutThatTruncatesToZero() {
        assertThrows(IllegalArgumentException.class, () -> configuration(Duration.ofNanos(500_000)).validate());
    }

    @Test
    void rejectsHandshakeTimeoutLargerThanSocketTimeoutRange() {
        assertThrows(IllegalArgumentException.class,
                () -> configuration(Duration.ofMillis(Integer.MAX_VALUE + 1L)).validate());
    }

    @Test
    void acceptsOneConnection() {
        assertDoesNotThrow(() -> RavenServerConfiguration.builder().port(0).handler(NOOP).maxConnections(1).build().validate());
    }

    @Test
    void rejectsZeroMaxConnections() {
        assertThrows(IllegalArgumentException.class,
                () -> RavenServerConfiguration.builder().port(0).handler(NOOP).maxConnections(0).build().validate());
    }

    private static RavenServerConfiguration configuration(final Duration handshakeTimeout) {
        return RavenServerConfiguration.builder()
                .port(0)
                .handler(NOOP)
                .handshakeTimeout(handshakeTimeout)
                .build();
    }
}
