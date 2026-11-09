package io.github.trimax.raven.core.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.experimental.UtilityClass;

/**
 * Threads of the transport: virtual, one per task, and named after their role, e.g. {@code raven-accept}. Also marks
 * the threads that run user callbacks: a method called from any callback never waits, since what it would wait for
 * may need that very callback to return first.
 */
@UtilityClass
public final class ThreadUtil {

    /** Bound on every thread that runs session callbacks. */
    private final ScopedValue<Boolean> CALLBACK_THREAD = ScopedValue.newInstance();

    /**
     * Returns an executor that starts a virtual thread per task. Closing it waits for all of them to end.
     */
    public ExecutorService newExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    }

    /**
     * Runs a task on a new thread of the executor under the given name.
     *
     * @throws java.util.concurrent.RejectedExecutionException if the executor is shut down
     */
    public void execute(final ExecutorService threads, final String name, final Runnable task) {
        threads.execute(() -> {
            Thread.currentThread().setName(name);
            task.run();
        });
    }

    /**
     * Runs {@code callbacks} on the current thread, marked as a callback thread.
     */
    public void runCallbacks(final Runnable callbacks) {
        ScopedValue.where(CALLBACK_THREAD, true).run(callbacks);
    }

    /**
     * Returns whether the current thread runs session callbacks.
     */
    public boolean isCallbackThread() {
        return CALLBACK_THREAD.isBound();
    }

    /**
     * Waits for a session milestone, unless the current thread is a callback thread. An interrupt ends the wait and
     * keeps the interrupt flag set; the milestone is still reached later.
     */
    public void awaitOutsideCallbacks(final CompletableFuture<?> milestone) {
        if (isCallbackThread())
            return;

        try {
            milestone.get();
        } catch (final InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException ex) {
            // Milestones never complete exceptionally
            throw new IllegalStateException(ex);
        }
    }
}
