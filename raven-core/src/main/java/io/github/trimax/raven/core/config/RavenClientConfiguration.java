package io.github.trimax.raven.core.config;

import java.util.List;

import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.core.interceptor.ClientMessageInterceptor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

/**
 * Configuration for {@link io.github.trimax.raven.core.RavenClient}.
 * Holds the handler, host, port, and an optional list of message interceptors.
 */
@Getter
@SuperBuilder
public final class RavenClientConfiguration extends AbstractRavenConfiguration {

    @NonNull
    private final String host;

    @NonNull
    private final ClientHandler handler;

    @NonNull
    @Builder.Default
    private final List<ClientMessageInterceptor> interceptors = List.of();
}
