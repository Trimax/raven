package io.github.trimax.raven.core.internal.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HandlingUtil}.
 */
class HandlingUtilTest {

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
