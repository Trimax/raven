package io.github.trimax.raven.core.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.util.interfaces.ThrowingRunnable;
import io.github.trimax.raven.core.util.interfaces.ThrowingSupplier;

/**
 * Unit tests for {@link HandlingUtil}.
 */
class HandlingUtilTest {

    /** Counts close() calls and can be told to fail on close. */
    private static final class Resource implements AutoCloseable {
        private final AtomicInteger closed = new AtomicInteger();
        private final boolean failOnClose;

        Resource(final boolean failOnClose) {
            this.failOnClose = failOnClose;
        }

        @Override
        public void close() throws IOException {
            closed.incrementAndGet();
            if (failOnClose)
                throw new IOException("close failed");
        }
    }

    // --- closeOnFailure(AutoCloseable, ThrowingSupplier) ---

    @Test
    void returnsResultAndKeepsResourceOpenOnSuccess() throws IOException {
        final var resource = new Resource(false);

        final var result = HandlingUtil.closeOnFailure(resource, (ThrowingSupplier<String, IOException>) () -> "ready");

        assertEquals("ready", result);
        assertEquals(0, resource.closed.get());
    }

    @Test
    void closesResourceAndRethrowsCheckedException() {
        final var resource = new Resource(false);
        final var failure = new IOException("setup failed");

        final var thrown = assertThrows(IOException.class, () -> HandlingUtil.closeOnFailure(resource,
                (ThrowingSupplier<String, IOException>) () -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(1, resource.closed.get());
    }

    @Test
    void closesResourceAndRethrowsRuntimeException() {
        final var resource = new Resource(false);
        final var failure = new IllegalStateException("setup failed");

        final var thrown = assertThrows(IllegalStateException.class, () -> HandlingUtil.closeOnFailure(resource,
                (ThrowingSupplier<String, IOException>) () -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(1, resource.closed.get());
    }

    @Test
    void failureToCloseDoesNotMaskOriginalException() {
        final var resource = new Resource(true);
        final var failure = new IOException("setup failed");

        final var thrown = assertThrows(IOException.class, () -> HandlingUtil.closeOnFailure(resource,
                (ThrowingSupplier<String, IOException>) () -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(1, resource.closed.get());
    }

    // --- closeOnFailure(AutoCloseable, ThrowingRunnable) ---

    @Test
    void runnableKeepsResourceOpenOnSuccess() throws IOException {
        final var resource = new Resource(false);
        final var ran = new AtomicBoolean();

        HandlingUtil.closeOnFailure(resource, (ThrowingRunnable<IOException>) () -> ran.set(true));

        assertTrue(ran.get());
        assertEquals(0, resource.closed.get());
    }

    @Test
    void runnableClosesResourceAndRethrows() {
        final var resource = new Resource(false);

        assertThrows(IOException.class, () -> HandlingUtil.closeOnFailure(resource, (ThrowingRunnable<IOException>) () -> {
            throw new IOException("setup failed");
        }));
        assertEquals(1, resource.closed.get());
    }

    // --- callHandler(Runnable, Supplier<String>) ---

    @Test
    void callHandlerReturnsTrueAndDoesNotBuildContextOnSuccess() {
        final var contextBuilt = new AtomicBoolean();

        final var result = HandlingUtil.callHandler(() -> { }, () -> {
            contextBuilt.set(true);
            return "context";
        });

        assertTrue(result);
        assertFalse(contextBuilt.get(), "the log context must only be built on failure");
    }

    @Test
    void callHandlerReturnsFalseWhenHandlerThrows() {
        final var result = HandlingUtil.callHandler(() -> {
            throw new IllegalStateException("handler failure");
        }, () -> "for test");

        assertFalse(result);
    }

    @Test
    void callHandlerDoesNotSwallowErrors() {
        assertThrows(AssertionError.class, () -> HandlingUtil.callHandler(() -> {
            throw new AssertionError("not a handler failure");
        }, () -> "for test"));
    }
}
