package io.github.trimax.raven.core.internal.session;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.config.RavenClientConfiguration;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.internal.util.HandlingUtil;
import io.github.trimax.raven.core.internal.util.InterceptorUtil;
import io.github.trimax.raven.core.validation.MessageValidator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * What happens to each message a session receives, shared by the server and the client: validation, the
 * interceptor chain, then the handler. One instance per session, used only by its session thread.
 *
 * <p>Internal: not part of the API.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class InboundPipeline {

    private final InvalidMessageLog invalidMessages;
    private final Predicate<Message> interceptors;
    private final Consumer<Message> handler;

    /** Describes a handler failure for the log; evaluated only on failure. */
    private final Supplier<String> failureContext;

    public static InboundPipeline of(final RavenServerConfiguration configuration, final Client client) {
        return new InboundPipeline(new InvalidMessageLog(client.getId().toString()),
                message -> InterceptorUtil.shouldProceed(configuration.getInterceptors(), client, message),
                message -> configuration.getHandler().onMessage(client, message),
                () -> "for message from " + client.getId() + ", closing its connection");
    }

    public static InboundPipeline of(final RavenClientConfiguration configuration) {
        return new InboundPipeline(new InvalidMessageLog("the server"),
                message -> InterceptorUtil.shouldProceed(configuration.getInterceptors(), message),
                message -> configuration.getHandler().onMessage(message),
                () -> "for received message, closing the connection");
    }

    /**
     * Validates a received message, passes it through the interceptor chain and dispatches it to the handler.
     *
     * @return {@code false} if the handler failed and the session must end
     */
    public boolean deliver(final Message message) {
        final var violations = MessageValidator.validate(message);
        if (!violations.isEmpty()) {
            invalidMessages.log(message, violations);
            return true;
        }

        if (!interceptors.test(message))
            return true;

        return HandlingUtil.callHandler(() -> handler.accept(message), failureContext);
    }

    /**
     * Called once the session has ended.
     */
    public void summarize() {
        invalidMessages.summarize();
    }
}
