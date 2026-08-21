package io.github.trimax.raven.server;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.handler.ServerHandler;
import io.github.trimax.raven.spring.AbstractMessageRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Server-side message router. Implements {@link ServerHandler} and dispatches
 * incoming messages and lifecycle events to annotated methods.
 *
 * <p>Scans for:
 * <ul>
 *   <li>{@link SubscribeMessage} — {@code void method(Client sender, T message)}</li>
 *   <li>{@link SubscribeConnect} — {@code void method(Client client)}</li>
 *   <li>{@link SubscribeDisconnect} — {@code void method(Client client)}</li>
 * </ul>
 */
@Slf4j
@Component
public final class ServerMessageRouter extends AbstractMessageRouter implements ServerHandler {

    @Override
    protected Class<? extends Annotation> messageAnnotation() {
        return SubscribeMessage.class;
    }

    @Override
    protected Class<? extends Annotation> connectAnnotation() {
        return SubscribeConnect.class;
    }

    @Override
    protected Class<? extends Annotation> disconnectAnnotation() {
        return SubscribeDisconnect.class;
    }

    @Override
    protected Class<? extends Message> getMessageType(final Annotation annotation) {
        return ((SubscribeMessage) annotation).value();
    }

    @Override
    public void onConnect(final Client client) {
        invokeConnectHandlers(handler -> handler.invoke(client));
    }

    @Override
    public void onDisconnect(final Client client) {
        invokeDisconnectHandlers(handler -> handler.invoke(client));
    }

    @Override
    public void onMessage(final Client sender, final Message message) {
        invokeMessageHandlers(message, handler -> handler.invoke(sender, message));
    }

    @Override
    protected void validateMessageHandler(final Method method,
                                          final Class<?> beanClass,
                                          final Class<? extends Message> messageType) {
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
        if (!messageType.isAssignableFrom(secondParam) && !secondParam.isAssignableFrom(messageType)) {
            throw new IllegalStateException(
                    "Invalid @SubscribeMessage method %s.%s: @SubscribeMessage(%s) does not match parameter type %s"
                            .formatted(beanClass.getSimpleName(), method.getName(),
                                    messageType.getSimpleName(), secondParam.getSimpleName()));
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
