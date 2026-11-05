package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.handler.ServerHandler;

/**
 * Verifies the validation of {@code handshakeTimeout} on {@link RavenServerConfiguration}.
 */
class RavenServerHandshakeConfigTest {

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
    void defaultsToFiveSeconds() {
        final var configuration = RavenServerConfiguration.builder().port(0).handler(NOOP).build();

        assertEquals(Duration.ofSeconds(5), configuration.getHandshakeTimeout());
    }

    @Test
    void acceptsPositiveTimeout() {
        final var configuration = RavenServerConfiguration.builder()
                .port(0)
                .handler(NOOP)
                .handshakeTimeout(Duration.ofMillis(250))
                .build();

        assertDoesNotThrow(() -> new RavenServer(configuration));
    }

    @Test
    void rejectsZeroTimeout() {
        final var configuration = RavenServerConfiguration.builder()
                .port(0)
                .handler(NOOP)
                .handshakeTimeout(Duration.ZERO)
                .build();

        assertThrows(IllegalArgumentException.class, () -> new RavenServer(configuration));
    }

    @Test
    void rejectsNegativeTimeout() {
        final var configuration = RavenServerConfiguration.builder()
                .port(0)
                .handler(NOOP)
                .handshakeTimeout(Duration.ofSeconds(-1))
                .build();

        assertThrows(IllegalArgumentException.class, () -> new RavenServer(configuration));
    }

    @Test
    void rejectsSubMillisecondTimeout() {
        // 0.5 ms truncates to a socket timeout of 0, which would mean "wait forever"
        final var configuration = RavenServerConfiguration.builder()
                .port(0)
                .handler(NOOP)
                .handshakeTimeout(Duration.ofNanos(500_000))
                .build();

        assertThrows(IllegalArgumentException.class, () -> new RavenServer(configuration));
    }

    @Test
    void rejectsTimeoutLargerThanSocketTimeoutRange() {
        final var configuration = RavenServerConfiguration.builder()
                .port(0)
                .handler(NOOP)
                .handshakeTimeout(Duration.ofMillis((long) Integer.MAX_VALUE + 1))
                .build();

        assertThrows(IllegalArgumentException.class, () -> new RavenServer(configuration));
    }

    @Test
    void rejectsNullTimeoutAtBuild() {
        assertThrows(NullPointerException.class, () -> RavenServerConfiguration.builder()
                .port(0)
                .handler(NOOP)
                .handshakeTimeout(null)
                .build());
    }
}
