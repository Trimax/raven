package io.github.trimax.raven.core.config;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;

import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

/**
 * Configuration for {@link io.github.trimax.raven.core.RavenServer}.
 * Holds the handler, port, optional bind address, connection limit, and an optional list of message interceptors.
 */
@Getter
@SuperBuilder
public final class RavenServerConfiguration extends AbstractRavenConfiguration {

    @NonNull
    private final ServerHandler handler;

    /**
     * Local address to listen on, e.g. the loopback address or one network interface.
     * {@code null} (the default) listens on all local addresses.
     */
    private final InetAddress bindAddress;

    /**
     * Interceptors invoked in list order on each incoming message before it reaches handlers.
     * The first interceptor that rejects (returns {@code false}) or throws stops the chain.
     */
    @NonNull
    @Builder.Default
    private final List<ServerMessageInterceptor> interceptors = List.of();

    /**
     * Maximum time a new connection may take to deliver its stream header before it is dropped.
     * Must be at least 1 millisecond and no larger than {@link Integer#MAX_VALUE} milliseconds (about 24.8 days).
     */
    @NonNull
    @Builder.Default
    private final Duration handshakeTimeout = Duration.ofSeconds(5);

    /**
     * Maximum number of connections served at once, counting both connections still in the handshake and
     * established sessions. A connection accepted beyond the limit is closed at once. Must be at least 1.
     */
    @Builder.Default
    private final int maxConnections = 1024;

    @Override
    void validateSpecific() {
        // A zero socket timeout means "wait forever", so a timeout that truncates to 0 ms would disable the protection
        final var timeout = getHandshakeTimeout();
        if (timeout.toMillis() < 1)
            throw new IllegalArgumentException("handshakeTimeout must be at least 1ms, got " + timeout);

        if (timeout.toMillis() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("handshakeTimeout is too large: " + timeout);

        if (maxConnections < 1)
            throw new IllegalArgumentException("maxConnections must be at least 1, got " + maxConnections);
    }
}
