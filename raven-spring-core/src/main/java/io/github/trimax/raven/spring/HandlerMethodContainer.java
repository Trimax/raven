package io.github.trimax.raven.spring;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.github.trimax.raven.spring.AbstractMessageRouter.HandlerMethod;

/**
 * Mutable, thread-safe collection of registered handlers for a single registration point
 * (one message type, connect, or disconnect).
 *
 * <p>Backed by a copy-on-write list: registration is rare and may happen late (lazy beans),
 * while invocation is frequent and concurrent. Invocation iterates over a snapshot, so handlers
 * registered during an invocation are not seen by it.
 */
final class HandlerMethodContainer {

    private final List<HandlerMethod> handlers = new CopyOnWriteArrayList<>();

    /**
     * Adds the handler unless the same bean instance with the same method is already registered.
     * Guards against the same bean instance being post-processed twice. Beans are compared by identity
     * because user beans may override {@code equals}.
     *
     * @return {@code true} if the handler was added
     */
    synchronized boolean addIfAbsent(final HandlerMethod candidate) {
        for (final var handler : handlers)
            if (handler.bean() == candidate.bean() && handler.method().equals(candidate.method()))
                return false;

        return handlers.add(candidate);
    }

    /**
     * Passes every handler to the invoker in registration order.
     */
    void invoke(final Consumer<HandlerMethod> invoker) {
        handlers.forEach(invoker);
    }

    /**
     * Returns a read-only live view of the registered handlers in registration order.
     */
    List<HandlerMethod> handlers() {
        return Collections.unmodifiableList(handlers);
    }

    /**
     * Returns the number of registered handlers.
     */
    int size() {
        return handlers.size();
    }
}
