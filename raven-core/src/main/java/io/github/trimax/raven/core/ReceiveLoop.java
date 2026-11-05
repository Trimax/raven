package io.github.trimax.raven.core;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;
import io.github.trimax.raven.core.util.InterceptorUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads messages from one client until the session ends. Each message is validated, passed through the
 * interceptor chain and dispatched to the handler.
 */
@Slf4j
@RequiredArgsConstructor
final class ReceiveLoop {

    /** Receives the validated, intercepted messages. */
    private final ServerHandler handler;

    /** Interceptor chain applied to every message before the handler. */
    private final List<ServerMessageInterceptor> interceptors;

    /** Whether the owning server is still running. */
    private final BooleanSupplier running;

    /** Invoked once the session ends, after the client has been disconnected. */
    private final Consumer<Client> onSessionEnded;

    /**
     * Runs the receive loop for the client on the current thread.
     * Ends when the client disconnects, the server stops, or the handler throws.
     * The client is always disconnected and {@code onSessionEnded} is always invoked afterward.
     */
    void run(final Client client) {
        try {
            while (client.isConnected() && running.getAsBoolean()) {
                final var message = client.receive();
                if (message == null)
                    break;

                final var violations = MessageValidator.validate(message);
                if (!violations.isEmpty()) {
                    log.warn("Received invalid {} from {}: {}", message.getClass().getSimpleName(), client.getId(), violations);
                    continue;
                }

                if (!InterceptorUtil.shouldProceed(interceptors, client, message))
                    continue;

                try {
                    handler.onMessage(client, message);
                } catch (final RuntimeException ex) {
                    // A failing handler ends this session; the finally block releases the connection
                    log.error("Handler failed for message from {}, closing connection: {}",
                            client.getId(), ex.getMessage(), ex);
                    return;
                }
            }
        } finally {
            client.disconnect();
            onSessionEnded.accept(client);
        }
    }
}
