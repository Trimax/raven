package io.github.trimax.raven.core.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.experimental.UtilityClass;

/**
 * Threads of the transport: virtual, one per task, and named after their role, e.g. {@code raven-accept}. Also marks
 * the threads that run user callbacks, so that a method called from a callback can avoid waiting for itself.
 */
@UtilityClass
public final class ThreadUtil {

    /** Bound on a callback thread to the object whose callbacks it runs. */
    private final ScopedValue<Object> CALLBACK_OWNER = ScopedValue.newInstance();

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
     * Runs {@code callbacks} on the current thread, marked as running the callbacks of {@code owner}.
     */
    public void runCallbacks(final Object owner, final Runnable callbacks) {
        ScopedValue.where(CALLBACK_OWNER, owner).run(callbacks);
    }

    /**
     * Returns the owner whose callbacks the current thread runs, or {@code null} outside a callback thread.
     */
    public Object callbackOwner() {
        return CALLBACK_OWNER.isBound() ? CALLBACK_OWNER.get() : null;
    }
}
