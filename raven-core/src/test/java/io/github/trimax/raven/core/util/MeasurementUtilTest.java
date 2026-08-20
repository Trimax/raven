package io.github.trimax.raven.core.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.util.interfaces.ThrowingRunnable;
import io.github.trimax.raven.core.util.interfaces.ThrowingSupplier;

/**
 * Unit tests for {@link MeasurementUtil}.
 */
class MeasurementUtilTest {

    @Test
    void supplierWithDurationConsumerReturnsResult() throws Exception {
        final var duration = new AtomicReference<Duration>();

        final var result = MeasurementUtil.measure(() -> "hello", duration::set);

        assertEquals("hello", result);
        assertNotNull(duration.get());
        assertFalse(duration.get().isNegative());
    }

    @Test
    void supplierWithDurationConsumerReportsDurationOnSuccess() throws Exception {
        final var duration = new AtomicReference<Duration>();

        MeasurementUtil.measure(() -> {
            Thread.sleep(50);
            return 42;
        }, duration::set);

        assertNotNull(duration.get());
        assertTrue(duration.get().toMillis() >= 40, "Duration should be at least ~50ms");
    }

    @Test
    void supplierWithDurationConsumerReportsDurationOnException() {
        final var duration = new AtomicReference<Duration>();

        assertThrows(IOException.class, () ->
                MeasurementUtil.<String, Exception>measure(() -> {
                    Thread.sleep(50);
                    throw new IOException("test error");
                }, duration::set)
        );

        assertNotNull(duration.get());
        assertTrue(duration.get().toMillis() >= 40, "Duration should be reported even on exception");
    }

    @Test
    void supplierWithDurationConsumerPropagatesCheckedException() {
        final var duration = new AtomicReference<Duration>();

        final var ex = assertThrows(IOException.class, () ->
                MeasurementUtil.<Void, IOException>measure(() -> {
                    throw new IOException("checked");
                }, duration::set)
        );

        assertEquals("checked", ex.getMessage());
        assertNotNull(duration.get());
    }

    @Test
    void supplierWithDurationConsumerPropagatesRuntimeException() {
        final var duration = new AtomicReference<Duration>();

        assertThrows(IllegalStateException.class, () ->
                MeasurementUtil.<Void, RuntimeException>measure(() -> {
                    throw new IllegalStateException("runtime");
                }, duration::set)
        );

        assertNotNull(duration.get());
    }

    @Test
    void supplierWithBiConsumerReturnsResultAndDuration() throws Exception {
        final var capturedResult = new AtomicReference<Optional<String>>();
        final var capturedDuration = new AtomicReference<Duration>();

        final var result = MeasurementUtil.measure(
                () -> "world",
                (opt, dur) -> {
                    capturedResult.set(opt);
                    capturedDuration.set(dur);
                }
        );

        assertEquals("world", result);
        assertTrue(capturedResult.get().isPresent());
        assertEquals("world", capturedResult.get().get());
        assertNotNull(capturedDuration.get());
        assertFalse(capturedDuration.get().isNegative());
    }

    @Test
    void supplierWithBiConsumerPassesEmptyOptionalOnException() {
        final var capturedResult = new AtomicReference<Optional<String>>();
        final var capturedDuration = new AtomicReference<Duration>();

        assertThrows(IOException.class, () ->
                MeasurementUtil.<String, IOException>measure(
                        () -> { throw new IOException("fail"); },
                        (opt, dur) -> {
                            capturedResult.set(opt);
                            capturedDuration.set(dur);
                        }
                )
        );

        assertNotNull(capturedResult.get());
        assertTrue(capturedResult.get().isEmpty());
        assertNotNull(capturedDuration.get());
    }

    @Test
    void supplierWithBiConsumerHandlesNullResult() throws Exception {
        final var capturedResult = new AtomicReference<Optional<String>>();
        final var capturedDuration = new AtomicReference<Duration>();

        final var result = MeasurementUtil.<String, Exception>measure(
                () -> null,
                (opt, dur) -> {
                    capturedResult.set(opt);
                    capturedDuration.set(dur);
                }
        );

        assertNull(result);
        assertNotNull(capturedResult.get());
        assertTrue(capturedResult.get().isEmpty());
        assertNotNull(capturedDuration.get());
    }

    @Test
    void runnableWithDurationConsumerReportsDuration() throws Exception {
        final var duration = new AtomicReference<Duration>();

        MeasurementUtil.measure(() -> Thread.sleep(50), duration::set);

        assertNotNull(duration.get());
        assertTrue(duration.get().toMillis() >= 40, "Duration should be at least ~50ms");
    }

    @Test
    void runnableWithDurationConsumerReportsDurationOnException() {
        final var duration = new AtomicReference<Duration>();

        assertThrows(IOException.class, () ->
                MeasurementUtil.<IOException>measure(() -> {
                    throw new IOException("runnable error");
                }, duration::set)
        );

        assertNotNull(duration.get());
    }

    @Test
    void runnableWithDurationConsumerExecutesRunnable() throws Exception {
        final var duration = new AtomicReference<Duration>();
        final var executed = new AtomicReference<>(false);

        MeasurementUtil.<RuntimeException>measure(() -> executed.set(true), duration::set);

        assertTrue(executed.get());
        assertNotNull(duration.get());
    }

    @Test
    void supplierWithDurationConsumerRejectsNullSupplier() {
        assertThrows(NullPointerException.class, () ->
                MeasurementUtil.measure(
                        (ThrowingSupplier<Object, RuntimeException>) null,
                        (Duration _) -> {}
                )
        );
    }

    @Test
    void supplierWithDurationConsumerRejectsNullConsumer() {
        assertThrows(NullPointerException.class, () ->
                MeasurementUtil.measure(() -> "x", (java.util.function.Consumer<Duration>) null)
        );
    }

    @Test
    void supplierWithBiConsumerRejectsNullSupplier() {
        assertThrows(NullPointerException.class, () ->
                MeasurementUtil.measure(null, (Optional<Object> opt, Duration d) -> {})
        );
    }

    @Test
    void supplierWithBiConsumerRejectsNullBiConsumer() {
        assertThrows(NullPointerException.class, () ->
                MeasurementUtil.measure(() -> "x", (java.util.function.BiConsumer<Optional<String>, Duration>) null)
        );
    }

    @Test
    void runnableWithDurationConsumerRejectsNullRunnable() {
        assertThrows(NullPointerException.class, () ->
                MeasurementUtil.<RuntimeException>measure(
                        (ThrowingRunnable<RuntimeException>) null,
                        (Duration d) -> {}
                )
        );
    }

    @Test
    void runnableWithDurationConsumerRejectsNullConsumer() {
        assertThrows(NullPointerException.class, () ->
                MeasurementUtil.<RuntimeException>measure(() -> {}, (java.util.function.Consumer<Duration>) null)
        );
    }
}
