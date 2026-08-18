package io.github.trimax.raven.client;

import io.github.trimax.raven.handler.ClientHandler;
import io.github.trimax.raven.Message;
import io.github.trimax.raven.spring.AbstractMessageRouter;
import io.github.trimax.raven.spring.SubscribeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Client-side message router. Implements {@link ClientHandler} and dispatches
 * incoming messages and lifecycle events to {@code @SubscribeMessage},
 * {@code @SubscribeConnect}, and {@code @SubscribeDisconnect} annotated methods.
 *
 * <p>Expected handler signatures:
 * <ul>
 *   <li>{@code @SubscribeMessage: void method(T message)}</li>
 *   <li>{@code @SubscribeConnect: void method()}</li>
 *   <li>{@code @SubscribeDisconnect: void method()}</li>
 * </ul>
 */
@Slf4j
@Component
public class ClientMessageRouter extends AbstractMessageRouter implements ClientHandler {

    @Override
    public void onConnect() {
        dispatchConnect();
    }

    @Override
    public void onDisconnect() {
        dispatchDisconnect();
    }

    @Override
    public void onMessage(final Message message) {
        dispatchMessage(message);
    }

    @Override
    protected void validateMessageHandler(final SubscribeMessage annotation,
                                          final Method method,
                                          final Class<?> beanClass) {
        if (method.getParameterCount() != 1) {
            throw new IllegalStateException(
                    "Invalid @SubscribeMessage method %s.%s: expected 1 parameter (Message), got %d"
                            .formatted(beanClass.getSimpleName(), method.getName(), method.getParameterCount()));
        }

        final var param = method.getParameterTypes()[0];
        final var annotatedType = annotation.value();
        if (!annotatedType.isAssignableFrom(param) && !param.isAssignableFrom(annotatedType)) {
            throw new IllegalStateException(
                    "Invalid @SubscribeMessage method %s.%s: @SubscribeMessage(%s) does not match parameter type %s"
                            .formatted(beanClass.getSimpleName(), method.getName(),
                                    annotatedType.getSimpleName(), param.getSimpleName()));
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
