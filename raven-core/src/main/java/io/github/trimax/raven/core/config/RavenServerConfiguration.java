package io.github.trimax.raven.core.config;

import java.util.List;

import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

/**
 * Configuration for {@link io.github.trimax.raven.core.RavenServer}.
 * Holds the handler, port, and an optional list of message interceptors.
 */
@Getter
@SuperBuilder
public final class RavenServerConfiguration extends AbstractRavenConfiguration {

    @NonNull
    private final ServerHandler handler;

    /**
     * Interceptors invoked in list order on each incoming message before it reaches handlers.
     * The first interceptor that rejects (returns {@code false}) or throws stops the chain.
     */
    @NonNull
    @Builder.Default
    private final List<ServerMessageInterceptor> interceptors = List.of();
}
