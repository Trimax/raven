package io.github.trimax.raven.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
 *
 * <p>Message dispatch follows the superclass chain of the message: handlers registered for the
 * concrete message class are invoked first, then handlers registered for each superclass up to
 * and including {@link Message}. Interfaces are not considered. The resolved handler list for each
 * concrete message class is computed once and cached; the cache is invalidated whenever a new
 * message handler is registered.
 */
@Slf4j
public abstract class AbstractMessageRouter implements BeanPostProcessor, SmartInitializingSingleton {

    private final Map<Class<? extends Message>, List<HandlerMethod>> messageHandlers = new ConcurrentHashMap<>();
    private final List<HandlerMethod> connectHandlers = new ArrayList<>();
    private final List<HandlerMethod> disconnectHandlers = new ArrayList<>();

    /**
     * Cache of resolved handlers per concrete message class. Replaced (not cleared) on registration,
     * so a resolution computed against the old registrations can only land in the discarded map.
     */
    private volatile Map<Class<?>, List<HandlerMethod>> resolvedHandlers = new ConcurrentHashMap<>();

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
            // Bridge methods resolve to the annotation of the bridged method and would register it twice
            if (method.isBridge())
                continue;

            final var msgAnnotation = AnnotationUtils.findAnnotation(method, messageAnnotation());
            if (msgAnnotation != null) {
                final var messageType = getMessageType(msgAnnotation);
                validateMessageHandler(method, targetClass, messageType);
                method.setAccessible(true);
                registerMessageHandler(messageType, new HandlerMethod(bean, method));
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

        messageHandlers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.<Class<?>, String>comparing(Class::getSimpleName)
                        .thenComparing(Class::getName)))
                .forEach(entry -> log.info("  {} -> {} handler(s)",
                        entry.getKey().getSimpleName(), entry.getValue().size()));
    }

    /**
     * Invokes all registered message handlers for the given message.
     *
     * <p>Handlers are invoked from the most specific type to the most general one: first those
     * registered for the message's own class, then for each superclass, ending with {@link Message}
     * (catch-all). Each handler method is invoked at most once per message.
     */
    protected void invokeMessageHandlers(final Message message, final Consumer<HandlerMethod> invoker) {
        final var handlers = handlersFor(message.getClass());
        if (handlers.isEmpty()) {
            log.debug("No handler for message type: {}", message.getClass().getSimpleName());
            return;
        }

        invokeHandlers(handlers, invoker);
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

    /**
     * Returns the cached, ordered list of handlers applicable to the given concrete message class.
     */
    List<HandlerMethod> handlersFor(final Class<?> messageClass) {
        return resolvedHandlers.computeIfAbsent(messageClass, this::resolveHandlers);
    }

    private void registerMessageHandler(final Class<? extends Message> messageType, final HandlerMethod handler) {
        messageHandlers.computeIfAbsent(messageType, _ -> new CopyOnWriteArrayList<>()).add(handler);
        resolvedHandlers = new ConcurrentHashMap<>();
    }

    private List<HandlerMethod> resolveHandlers(final Class<?> messageClass) {
        final List<HandlerMethod> result = new ArrayList<>();

        for (Class<?> current = messageClass; current != null && Message.class.isAssignableFrom(current); current = current.getSuperclass())
            for (final var handler : messageHandlers.getOrDefault(current, List.of()))
                if (!containsHandler(result, handler))
                    result.add(handler);

        return List.copyOf(result);
    }

    private static boolean containsHandler(final List<HandlerMethod> handlers, final HandlerMethod candidate) {
        for (final var handler : handlers)
            if (handler.bean() == candidate.bean() && handler.method().equals(candidate.method()))
                return true;

        return false;
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
