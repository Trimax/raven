package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.handler.ClientHandler;

/**
 * Verifies the validation of {@code connectTimeout} on {@link RavenClientConfiguration}.
 */
class RavenClientConnectConfigTest {

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
    void defaultsToTenSeconds() {
        final var configuration = RavenClientConfiguration.builder().host("localhost").port(0).handler(NOOP).build();

        assertEquals(Duration.ofSeconds(10), configuration.getConnectTimeout());
    }

    @Test
    void acceptsOneMillisecond() {
        assertDoesNotThrow(() -> new RavenClient(configuration(Duration.ofMillis(1))));
    }

    @Test
    void rejectsZeroTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new RavenClient(configuration(Duration.ZERO)));
    }

    @Test
    void rejectsNegativeTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new RavenClient(configuration(Duration.ofSeconds(-1))));
    }

    @Test
    void rejectsSubMillisecondTimeout() {
        // Truncates to a zero socket timeout, which would mean "wait forever"
        assertThrows(IllegalArgumentException.class, () -> new RavenClient(configuration(Duration.ofNanos(999_999))));
    }

    @Test
    void rejectsTimeoutLargerThanSocketTimeoutRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new RavenClient(configuration(Duration.ofMillis((long) Integer.MAX_VALUE + 1))));
    }

    @Test
    void rejectsNullTimeoutAtBuild() {
        assertThrows(NullPointerException.class, () -> configuration(null));
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
