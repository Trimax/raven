package io.github.trimax.raven.spring;

import io.github.trimax.raven.Client;
import io.github.trimax.raven.Message;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for message routers that scan Spring beans for
 * {@link SubscribeMessage}, {@link SubscribeConnect}, and {@link SubscribeDisconnect}
 * annotated methods and dispatch events to them.
 *
 * <p>Subclasses define the expected method signatures via
 * {@link #validateMessageHandler}, {@link #validateLifecycleHandler}.
 */
@Slf4j
public abstract class AbstractMessageRouter implements BeanPostProcessor, SmartInitializingSingleton {

    private final Map<Class<? extends Message>, List<HandlerMethod>> messageHandlers = new ConcurrentHashMap<>();
    private final List<HandlerMethod> connectHandlers = new ArrayList<>();
    private final List<HandlerMethod> disconnectHandlers = new ArrayList<>();

    @Override
    public Object postProcessAfterInitialization(final @NonNull Object bean,
                                                 final @NonNull String beanName) throws BeansException {
        for (final var method : bean.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribeMessage.class)) {
                final var annotation = method.getAnnotation(SubscribeMessage.class);
                validateMessageHandler(annotation, method, bean.getClass());
                method.setAccessible(true);
                messageHandlers.computeIfAbsent(annotation.value(), _ -> new ArrayList<>())
                        .add(new HandlerMethod(bean, method));
            }
            if (method.isAnnotationPresent(SubscribeConnect.class)) {
                validateLifecycleHandler(method, bean.getClass(), "SubscribeConnect");
                method.setAccessible(true);
                connectHandlers.add(new HandlerMethod(bean, method));
            }
            if (method.isAnnotationPresent(SubscribeDisconnect.class)) {
                validateLifecycleHandler(method, bean.getClass(), "SubscribeDisconnect");
                method.setAccessible(true);
                disconnectHandlers.add(new HandlerMethod(bean, method));
            }
        }
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.info("MessageRouter: {} message type(s), {} connect handler(s), {} disconnect handler(s)",
                messageHandlers.size(), connectHandlers.size(), disconnectHandlers.size());

        for (final var entry : messageHandlers.entrySet()) {
            log.info("  {} -> {} handler(s)", entry.getKey().getSimpleName(), entry.getValue().size());
        }
    }

    /**
     * Dispatches a message to registered handlers (server-side: with Client).
     */
    protected void dispatchMessage(final Client sender, final Message message) {
        dispatch(message, handler -> handler.invoke(sender, message));
    }

    /**
     * Dispatches a message to registered handlers (client-side: without Client).
     */
    protected void dispatchMessage(final Message message) {
        dispatch(message, handler -> handler.invoke(message));
    }

    private void dispatch(final Message message, final java.util.function.Consumer<HandlerMethod> invoker) {
        final var handlers = messageHandlers.get(message.getClass());
        if (CollectionUtils.isEmpty(handlers)) {
            log.debug("No handler for message type: {}", message.getClass().getSimpleName());
            return;
        }

        for (final var handler : handlers) {
            invoker.accept(handler);
        }
    }

    /**
     * Dispatches connect event (server-side: with Client).
     */
    protected void dispatchConnect(final Client client) {
        for (final var handler : connectHandlers) {
            handler.invoke(client);
        }
    }

    /**
     * Dispatches connect event (client-side: no args).
     */
    protected void dispatchConnect() {
        for (final var handler : connectHandlers) {
            handler.invoke();
        }
    }

    /**
     * Dispatches disconnect event (server-side: with Client).
     */
    protected void dispatchDisconnect(final Client client) {
        for (final var handler : disconnectHandlers) {
            handler.invoke(client);
        }
    }

    /**
     * Dispatches disconnect event (client-side: no args).
     */
    protected void dispatchDisconnect() {
        for (final var handler : disconnectHandlers) {
            handler.invoke();
        }
    }

    /**
     * Validates the signature of a @SubscribeMessage method.
     * Subclasses define expected param count and types.
     */
    protected abstract void validateMessageHandler(SubscribeMessage annotation, Method method, Class<?> beanClass);

    /**
     * Validates the signature of a @SubscribeConnect or @SubscribeDisconnect method.
     * Subclasses define expected param count and types.
     */
    protected abstract void validateLifecycleHandler(Method method, Class<?> beanClass, String annotationName);

    /**
     * Encapsulates a bean + method pair for reflective invocation.
     */
    protected record HandlerMethod(Object bean, Method method) {

        void invoke(final Client sender, final Message message) {
            try {
                method.invoke(bean, sender, message);
            } catch (final IllegalAccessException | InvocationTargetException ex) {
                log.error("Error invoking handler {}: {}", method.getName(), ex.getMessage(), ex);
            }
        }

        void invoke(final Message message) {
            try {
                method.invoke(bean, message);
            } catch (final IllegalAccessException | InvocationTargetException ex) {
                log.error("Error invoking handler {}: {}", method.getName(), ex.getMessage(), ex);
            }
        }

        void invoke(final Client client) {
            try {
                method.invoke(bean, client);
            } catch (final IllegalAccessException | InvocationTargetException ex) {
                log.error("Error invoking handler {}: {}", method.getName(), ex.getMessage(), ex);
            }
        }

        void invoke() {
            try {
                method.invoke(bean);
            } catch (final IllegalAccessException | InvocationTargetException ex) {
                log.error("Error invoking handler {}: {}", method.getName(), ex.getMessage(), ex);
            }
        }
    }
}
