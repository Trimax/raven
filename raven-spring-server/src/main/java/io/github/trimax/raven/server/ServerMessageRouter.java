package io.github.trimax.raven.server;

import io.github.trimax.raven.Client;
import io.github.trimax.raven.Message;
import io.github.trimax.raven.handler.ServerHandler;
import io.github.trimax.raven.spring.AbstractMessageRouter;
import io.github.trimax.raven.spring.SubscribeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Server-side message router. Implements {@link ServerHandler} and dispatches
 * incoming messages and lifecycle events to {@code @SubscribeMessage},
 * {@code @SubscribeConnect}, and {@code @SubscribeDisconnect} annotated methods.
 *
 * <p>Expected handler signatures:
 * <ul>
 *   <li>{@code @SubscribeMessage: void method(Client sender, T message)}</li>
 *   <li>{@code @SubscribeConnect: void method(Client client)}</li>
 *   <li>{@code @SubscribeDisconnect: void method(Client client)}</li>
 * </ul>
 */
@Slf4j
@Component
public class ServerMessageRouter extends AbstractMessageRouter implements ServerHandler {

    @Override
    public void onConnect(final Client client) {
        dispatchConnect(client);
    }

    @Override
    public void onDisconnect(final Client client) {
        dispatchDisconnect(client);
    }

    @Override
    public void onMessage(final Client sender, final Message message) {
        dispatchMessage(sender, message);
    }

    @Override
    protected void validateMessageHandler(final SubscribeMessage annotation,
                                          final Method method,
                                          final Class<?> beanClass) {
        if (method.getParameterCount() != 2) {
            throw new IllegalStateException(
                    "Invalid @SubscribeMessage method %s.%s: expected 2 parameters (Client, Message), got %d"
                            .formatted(beanClass.getSimpleName(), method.getName(), method.getParameterCount()));
        }

        final var firstParam = method.getParameterTypes()[0];
        if (!Client.class.isAssignableFrom(firstParam)) {
            throw new IllegalStateException(
                    "Invalid @SubscribeMessage method %s.%s: first parameter must be Client, got %s"
                            .formatted(beanClass.getSimpleName(), method.getName(), firstParam.getSimpleName()));
        }

        final var secondParam = method.getParameterTypes()[1];
        final var annotatedType = annotation.value();
        if (!annotatedType.isAssignableFrom(secondParam) && !secondParam.isAssignableFrom(annotatedType)) {
            throw new IllegalStateException(
                    "Invalid @SubscribeMessage method %s.%s: @SubscribeMessage(%s) does not match parameter type %s"
                            .formatted(beanClass.getSimpleName(), method.getName(),
                                    annotatedType.getSimpleName(), secondParam.getSimpleName()));
        }
    }

    @Override
    protected void validateLifecycleHandler(final Method method,
                                            final Class<?> beanClass,
                                            final String annotationName) {
        if (method.getParameterCount() != 1) {
            throw new IllegalStateException(
                    "Invalid @%s method %s.%s: expected 1 parameter (Client), got %d"
                            .formatted(annotationName, beanClass.getSimpleName(),
                                    method.getName(), method.getParameterCount()));
        }

        final var param = method.getParameterTypes()[0];
        if (!Client.class.isAssignableFrom(param)) {
            throw new IllegalStateException(
                    "Invalid @%s method %s.%s: parameter must be Client, got %s"
                            .formatted(annotationName, beanClass.getSimpleName(),
                                    method.getName(), param.getSimpleName()));
        }
    }
}
