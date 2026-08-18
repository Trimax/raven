package io.github.trimax.raven.client;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.handler.ClientHandler;
import io.github.trimax.raven.spring.AbstractMessageRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Client-side message router. Implements {@link ClientHandler} and dispatches
 * incoming messages and lifecycle events to annotated methods.
 *
 * <p>Scans for:
 * <ul>
 *   <li>{@link SubscribeMessage} — {@code void method(T message)}</li>
 *   <li>{@link SubscribeConnect} — {@code void method()}</li>
 *   <li>{@link SubscribeDisconnect} — {@code void method()}</li>
 * </ul>
 */
@Slf4j
@Component
public final class ClientMessageRouter extends AbstractMessageRouter implements ClientHandler {

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
    public void onConnect() {
        invokeConnectHandlers(HandlerMethod::invoke);
    }

    @Override
    public void onDisconnect() {
        invokeDisconnectHandlers(HandlerMethod::invoke);
    }

    @Override
    public void onMessage(final Message message) {
        invokeMessageHandlers(message, handler -> handler.invoke(message));
    }

    @Override
    protected void validateMessageHandler(final Method method,
                                          final Class<?> beanClass,
                                          final Class<? extends Message> messageType) {
        if (method.getParameterCount() != 1) {
            throw new IllegalStateException(
                    "Invalid @SubscribeMessage method %s.%s: expected 1 parameter (Message), got %d"
                            .formatted(beanClass.getSimpleName(), method.getName(), method.getParameterCount()));
        }

        final var param = method.getParameterTypes()[0];
        if (!messageType.isAssignableFrom(param) && !param.isAssignableFrom(messageType)) {
            throw new IllegalStateException(
                    "Invalid @SubscribeMessage method %s.%s: @SubscribeMessage(%s) does not match parameter type %s"
                            .formatted(beanClass.getSimpleName(), method.getName(),
                                    messageType.getSimpleName(), param.getSimpleName()));
        }
    }

    @Override
    protected void validateLifecycleHandler(final Method method,
                                            final Class<?> beanClass,
                                            final String annotationName) {
        if (method.getParameterCount() != 0) {
            throw new IllegalStateException(
                    "Invalid @%s method %s.%s: expected 0 parameters, got %d"
                            .formatted(annotationName, beanClass.getSimpleName(),
                                    method.getName(), method.getParameterCount()));
        }
    }
}
