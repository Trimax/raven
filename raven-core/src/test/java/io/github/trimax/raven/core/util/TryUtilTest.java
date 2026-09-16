package io.github.trimax.raven.core.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.util.interfaces.ThrowingRunnable;
import io.github.trimax.raven.core.util.interfaces.ThrowingSupplier;

/**
 * Unit tests for {@link TryUtil}.
 */
class TryUtilTest {

    // --- execute(ThrowingSupplier, T defaultValue) ---

    @Test
    void supplierWithDefaultReturnsResultOnSuccess() {
        final var result = TryUtil.execute(() -> "hello", "fallback");

        assertEquals("hello", result);
    }

    @Test
    void supplierWithDefaultReturnsNullResultWhenSupplierReturnsNull() {
        final var result = TryUtil.execute(() -> null, "fallback");

        assertNull(result);
    }

    @Test
    void supplierWithDefaultReturnsDefaultOnCheckedException() {
        final var result = TryUtil.execute(() -> {
            throw new IOException("fail");
        }, "fallback");

        assertEquals("fallback", result);
    }

    @Test
    void supplierWithDefaultReturnsDefaultOnRuntimeException() {
        final var result = TryUtil.execute(() -> {
            throw new IllegalStateException("fail");
        }, 42);

        assertEquals(42, result);
    }

    @Test
    void supplierWithDefaultReturnsNullDefaultWhenConfigured() {
        final var result = TryUtil.execute(() -> {
            throw new IOException("fail");
        }, (String) null);

        assertNull(result);
    }

    @Test
    void supplierWithDefaultRejectsNullSupplier() {
        final ThrowingSupplier<String, RuntimeException> supplier = null;
        assertThrows(NullPointerException.class, () -> TryUtil.execute(supplier, "fallback"));
    }

    // --- execute(ThrowingSupplier, Function onFailure) ---

    @Test
    void supplierWithFallbackReturnsResultOnSuccess() {
        final Function<RuntimeException, String> fallback = _ -> "fallback";

        final var result = TryUtil.<String, RuntimeException>execute(() -> "hello", fallback);

        assertEquals("hello", result);
    }

    @Test
    void supplierWithFallbackReturnsFallbackValueOnException() {
        final Function<IOException, String> fallback = _ -> "recovered";

        final var result = TryUtil.<String, IOException>execute(() -> {
            throw new IOException("fail");
        }, fallback);

        assertEquals("recovered", result);
    }

    @Test
    void supplierWithFallbackReceivesTheException() {
        final var captured = new AtomicReference<Exception>();
        final Function<IOException, String> fallback = e -> {
            captured.set(e);
            return "recovered";
        };

        TryUtil.<String, IOException>execute(() -> {
            throw new IOException("test error");
        }, fallback);

        assertNotNull(captured.get());
        assertInstanceOf(IOException.class, captured.get());
        assertEquals("test error", captured.get().getMessage());
    }

    @Test
    void supplierWithFallbackHandlesRuntimeException() {
        final Function<IllegalArgumentException, String> fallback = _ -> "default";

        final var result = TryUtil.<String, IllegalArgumentException>execute(() -> {
            throw new IllegalArgumentException("bad arg");
        }, fallback);

        assertEquals("default", result);
    }

    @Test
    void supplierWithFallbackCanReturnNull() {
        final Function<IOException, String> fallback = _ -> null;

        final var result = TryUtil.<String, IOException>execute(() -> {
            throw new IOException("fail");
        }, fallback);

        assertNull(result);
    }

    @Test
    void supplierWithFallbackRejectsNullSupplier() {
        final Function<RuntimeException, String> fallback = _ -> "fallback";
        final ThrowingSupplier<String, RuntimeException> supplier = null;

        assertThrows(NullPointerException.class, () -> TryUtil.execute(supplier, fallback));
    }

    @Test
    void supplierWithFallbackRejectsNullFunction() {
        final Function<RuntimeException, String> onFallback = null;

        assertThrows(NullPointerException.class, () -> TryUtil.<String, RuntimeException>execute(() -> "hello", onFallback));
    }

    // --- execute(ThrowingRunnable) ---

    @Test
    void runnableExecutesSuccessfully() {
        final var executed = new AtomicReference<>(false);

        TryUtil.execute(() -> executed.set(true));

        assertTrue(executed.get());
    }

    @Test
    void runnableSwallowsCheckedException() {
        assertDoesNotThrow(() ->
                TryUtil.execute(() -> {
                    throw new IOException("swallowed");
                })
        );
    }

    @Test
    void runnableSwallowsRuntimeException() {
        assertDoesNotThrow(() ->
                TryUtil.execute(() -> {
                    throw new IllegalStateException("swallowed");
                })
        );
    }

    @Test
    void runnableRejectsNullRunnable() {
        assertThrows(NullPointerException.class, () -> TryUtil.execute((ThrowingRunnable<RuntimeException>) null));
    }

    // --- execute(ThrowingRunnable, Consumer onFailure) ---

    @Test
    void runnableWithConsumerExecutesSuccessfully() {
        final var executed = new AtomicReference<>(false);
        final var consumerCalled = new AtomicReference<>(false);
        final Consumer<RuntimeException> onFailure = _ -> consumerCalled.set(true);

        TryUtil.execute(() -> executed.set(true), onFailure);

        assertTrue(executed.get());
        assertFalse(consumerCalled.get());
    }

    @Test
    void runnableWithConsumerDelegatesToConsumerOnException() {
        final var captured = new AtomicReference<Exception>();
        final Consumer<IOException> onFailure = captured::set;

        TryUtil.<IOException>execute(() -> {
            throw new IOException("runnable error");
        }, onFailure);

        assertNotNull(captured.get());
        assertInstanceOf(IOException.class, captured.get());
        assertEquals("runnable error", captured.get().getMessage());
    }

    @Test
    void runnableWithConsumerHandlesRuntimeException() {
        final var captured = new AtomicReference<Exception>();
        final Consumer<RuntimeException> onFailure = captured::set;

        TryUtil.<RuntimeException>execute(() -> {
            throw new IllegalStateException("runtime error");
        }, onFailure);

        assertNotNull(captured.get());
        assertInstanceOf(IllegalStateException.class, captured.get());
    }

    @Test
    void runnableWithConsumerRejectsNullRunnable() {
        final Consumer<RuntimeException> onFailure = _ -> {};

        assertThrows(NullPointerException.class, () ->
                TryUtil.execute((ThrowingRunnable<RuntimeException>) null, onFailure)
        );
    }

    @Test
    void runnableWithConsumerRejectsNullConsumer() {
        final ThrowingRunnable<RuntimeException> runnable = () -> {};
        final Consumer<RuntimeException> onFailure = null;

        assertThrows(NullPointerException.class, () -> TryUtil.execute(runnable, onFailure));
    }
}
