package io.github.trimax.raven.core.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.handler.ClientHandler;

/**
 * Verifies {@link RavenClientConfiguration#validate()} directly, without creating a client.
 */
class RavenClientConfigurationTest {

    private static final ClientHandler NOOP = new ClientHandler() {
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
    void defaultsAreValid() {
        assertDoesNotThrow(() -> configuration(Duration.ofSeconds(10)).validate());
    }

    @Test
    void acceptsOneMillisecond() {
        assertDoesNotThrow(() -> configuration(Duration.ofMillis(1)).validate());
    }

    @Test
    void rejectsZeroConnectTimeout() {
        assertThrows(IllegalArgumentException.class, () -> configuration(Duration.ZERO).validate());
    }

    @Test
    void rejectsNegativeConnectTimeout() {
        assertThrows(IllegalArgumentException.class, () -> configuration(Duration.ofMillis(-1)).validate());
    }

    @Test
    void rejectsConnectTimeoutThatTruncatesToZero() {
        assertThrows(IllegalArgumentException.class, () -> configuration(Duration.ofNanos(500_000)).validate());
    }

    @Test
    void rejectsConnectTimeoutLargerThanSocketTimeoutRange() {
        assertThrows(IllegalArgumentException.class,
                () -> configuration(Duration.ofMillis(Integer.MAX_VALUE + 1L)).validate());
    }

    private static RavenClientConfiguration configuration(final Duration connectTimeout) {
        return RavenClientConfiguration.builder()
                .host("localhost")
                .port(0)
                .handler(NOOP)
                .connectTimeout(connectTimeout)
                .build();
    }
}
