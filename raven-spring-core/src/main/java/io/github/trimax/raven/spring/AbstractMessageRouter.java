package io.github.trimax.raven.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;

import io.github.trimax.raven.core.Message;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for message routers that scan Spring beans for annotated handler methods.
 *
 * <p>Subclasses specify which annotations to scan for and how to validate method signatures.
 */
@Slf4j
public abstract class AbstractMessageRouter implements BeanPostProcessor, SmartInitializingSingleton {

    private final Map<Class<? extends Message>, List<HandlerMethod>> messageHandlers = new ConcurrentHashMap<>();
    private final List<HandlerMethod> connectHandlers = new ArrayList<>();
    private final List<HandlerMethod> disconnectHandlers = new ArrayList<>();

    /**
     * Returns the annotation class used for message handlers.
     */
    protected abstract Class<? extends Annotation> messageAnnotation();

    /**
     * Returns the annotation class used for connection handlers.
     */
    protected abstract Class<? extends Annotation> connectAnnotation();

    /**
     * Returns the annotation class used for disconnect handlers.
     */
    protected abstract Class<? extends Annotation> disconnectAnnotation();

    /**
     * Extracts the message type from the message annotation instance.
     */
    protected abstract Class<? extends Message> getMessageType(Annotation annotation);

    /**
     * Validates the signature of a message handler method.
     */
    protected abstract void validateMessageHandler(Method method, Class<?> beanClass, Class<? extends Message> messageType);

    /**
     * Validates the signature of a lifecycle (connect/disconnect) handler method.
     */
    protected abstract void validateLifecycleHandler(Method method, Class<?> beanClass, String annotationName);

    @Override
    public Object postProcessAfterInitialization(final @NonNull Object bean,
                                                 final @NonNull String beanName) throws BeansException {
        final var targetClass = AopUtils.getTargetClass(bean);

        for (final var method : targetClass.getDeclaredMethods()) {
            final var msgAnnotation = AnnotationUtils.findAnnotation(method, messageAnnotation());
            if (msgAnnotation != null) {
                final var messageType = getMessageType(msgAnnotation);
                validateMessageHandler(method, targetClass, messageType);
                method.setAccessible(true);
                messageHandlers.computeIfAbsent(messageType, _ -> new ArrayList<>())
                        .add(new HandlerMethod(bean, method));
            }

            if (AnnotationUtils.findAnnotation(method, connectAnnotation()) != null) {
                validateLifecycleHandler(method, targetClass, "SubscribeConnect");
                method.setAccessible(true);
                connectHandlers.add(new HandlerMethod(bean, method));
            }

            if (AnnotationUtils.findAnnotation(method, disconnectAnnotation()) != null) {
                validateLifecycleHandler(method, targetClass, "SubscribeDisconnect");
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
     * Invokes all registered message handlers for the given message type.
     * Also invokes handlers registered for the base {@link Message} type (catch-all),
     * unless the message itself is exactly {@link Message}.
     */
    protected void invokeMessageHandlers(final Message message, final Consumer<HandlerMethod> invoker) {
        final var specificHandlers = messageHandlers.getOrDefault(message.getClass(), List.of());
        final var genericHandlers = messageHandlers.getOrDefault(Message.class, List.of());

        if (specificHandlers.isEmpty() && genericHandlers.isEmpty()) {
            log.debug("No handler for message type: {}", message.getClass().getSimpleName());
            return;
        }

        invokeHandlers(specificHandlers, invoker);
        invokeHandlers(genericHandlers, invoker);
    }

    /**
     * Invokes all registered connection handlers.
     */
    protected void invokeConnectHandlers(final Consumer<HandlerMethod> invoker) {
        invokeHandlers(connectHandlers, invoker);
    }

    /**
     * Invokes all registered disconnect handlers.
     */
    protected void invokeDisconnectHandlers(final Consumer<HandlerMethod> invoker) {
        invokeHandlers(disconnectHandlers, invoker);
    }

    private void invokeHandlers(final List<HandlerMethod> handlers, final Consumer<HandlerMethod> invoker) {
        handlers.forEach(invoker);
    }

    /**
     * A bean + method pair representing a registered handler.
     */
    protected record HandlerMethod(Object bean, Method method) {

        /**
         * Invokes the handler method with the given arguments.
         * Exceptions are logged and swallowed to ensure other handlers still execute.
         */
        public void invoke(final Object... args) {
            try {
                method.invoke(bean, args);
            } catch (final Exception ex) {
                log.error("Error invoking handler {}.{}: {}",
                        bean.getClass().getSimpleName(), method.getName(), ex.getMessage(), ex);
            }
        }
    }
}
