package io.github.trimax.raven.core.config;

import java.time.Duration;
import java.util.List;

import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.interceptor.ClientMessageInterceptor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

/**
 * Configuration for {@link io.github.trimax.raven.core.RavenClient}.
 * Holds the handler, host, port, connect timeout, and an optional list of message interceptors.
 */
@Getter
@SuperBuilder
public final class RavenClientConfiguration extends AbstractRavenConfiguration {

    @NonNull
    private final String host;

    @NonNull
    private final ClientHandler handler;

    /**
     * Interceptors invoked in list order on each incoming message before it reaches handlers.
     * The first interceptor that rejects (returns {@code false}) or throws stops the chain.
     */
    @NonNull
    @Builder.Default
    private final List<ClientMessageInterceptor> interceptors = List.of();

    /**
     * Maximum time to establish the TCP connection and to receive the server's stream header.
     * Must be at least one millisecond and no larger than {@link Integer#MAX_VALUE} milliseconds (about 24.8 days).
     */
    @NonNull
    @Builder.Default
    private final Duration connectTimeout = Duration.ofSeconds(10);
}
