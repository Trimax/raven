package io.github.trimax.raven.spring;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.spring.AbstractMessageRouter.HandlerMethod;

/**
 * Unit tests for {@link HandlerMethodContainer}.
 */
class HandlerMethodContainerTest {

    private static final Method FIRST = method("first");
    private static final Method SECOND = method("second");

    private final HandlerMethodContainer container = new HandlerMethodContainer();

    @Test
    void newContainerIsEmpty() {
        assertEquals(0, container.size());
        assertTrue(container.handlers().isEmpty());
    }

    @Test
    void handlersKeepRegistrationOrder() {
        final var a = new HandlerMethod(new Target(), FIRST);
        final var b = new HandlerMethod(new Target(), SECOND);
        final var c = new HandlerMethod(new Target(), FIRST);

        container.addIfAbsent(a);
        container.addIfAbsent(b);
        container.addIfAbsent(c);

        assertEquals(List.of(a, b, c), container.handlers());
        assertEquals(3, container.size());
    }

    @Test
    void sameBeanAndMethodIsAddedOnce() {
        final var bean = new Target();

        assertTrue(container.addIfAbsent(new HandlerMethod(bean, FIRST)));
        assertFalse(container.addIfAbsent(new HandlerMethod(bean, FIRST)));

        assertEquals(1, container.size());
    }

    @Test
    void sameBeanWithDifferentMethodsIsAddedTwice() {
        final var bean = new Target();

        assertTrue(container.addIfAbsent(new HandlerMethod(bean, FIRST)));
        assertTrue(container.addIfAbsent(new HandlerMethod(bean, SECOND)));

        assertEquals(2, container.size());
    }

    @Test
    void beansAreComparedByIdentityNotEquals() {
        assertTrue(container.addIfAbsent(new HandlerMethod(new EqualToEverything(), FIRST)));
        assertTrue(container.addIfAbsent(new HandlerMethod(new EqualToEverything(), FIRST)));

        assertEquals(2, container.size());
    }

    @Test
    void invokePassesHandlersInRegistrationOrder() {
        final var a = new HandlerMethod(new Target(), FIRST);
        final var b = new HandlerMethod(new Target(), SECOND);
        container.addIfAbsent(a);
        container.addIfAbsent(b);

        final List<HandlerMethod> invoked = new ArrayList<>();
        container.invoke(invoked::add);

        assertEquals(List.of(a, b), invoked);
    }

    @Test
    void invokeOnEmptyContainerDoesNothing() {
        final List<HandlerMethod> invoked = new ArrayList<>();

        container.invoke(invoked::add);

        assertTrue(invoked.isEmpty());
    }

    @Test
    void registrationDuringInvocationDoesNotAffectRunningInvocation() {
        final var a = new HandlerMethod(new Target(), FIRST);
        final var late = new HandlerMethod(new Target(), SECOND);
        container.addIfAbsent(a);

        final List<HandlerMethod> invoked = new ArrayList<>();
        container.invoke(handler -> {
            invoked.add(handler);
            container.addIfAbsent(late);
        });

        assertEquals(List.of(a), invoked);
        assertEquals(List.of(a, late), container.handlers());
    }

    @Test
    void handlersViewIsReadOnly() {
        final var view = container.handlers();
        final var handler = new HandlerMethod(new Target(), FIRST);

        assertThrows(UnsupportedOperationException.class, () -> view.add(handler));
    }

    @Test
    void handlersViewReflectsLaterRegistrations() {
        final var view = container.handlers();
        final var handler = new HandlerMethod(new Target(), FIRST);

        container.addIfAbsent(handler);

        assertEquals(1, view.size());
        assertSame(handler, view.getFirst());
    }

    @Test
    void concurrentRegistrationOfSameHandlerAddsItOnce() throws Exception {
        final var bean = new Target();
        final int threads = 32;
        final var start = new CountDownLatch(1);

        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++)
                results.add(executor.submit(() -> {
                    start.await();
                    return container.addIfAbsent(new HandlerMethod(bean, FIRST));
                }));

            start.countDown();

            int added = 0;
            for (final var result : results)
                if (result.get(5, TimeUnit.SECONDS))
                    added++;

            assertEquals(1, added);
        }

        assertEquals(1, container.size());
    }

    private static Method method(final String name) {
        try {
            return Target.class.getDeclaredMethod(name);
        } catch (final NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // --- Fixtures ---

    @SuppressWarnings("unused")
    static final class Target {
        void first() {
        }

        void second() {
        }
    }

    static final class EqualToEverything {
        @Override
        public boolean equals(final Object other) {
            return true;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
