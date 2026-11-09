package io.github.trimax.raven.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.experimental.UtilityClass;

/**
 * The threads of the transport: virtual, one per task, and named after their role, e.g. {@code raven-accept}.
 */
@UtilityClass
final class TransportThreads {

    /** Bound on a session thread to the session's listener, so a callback can be recognised as one. */
    private final ScopedValue<SessionListener> CALLBACK_LISTENER = ScopedValue.newInstance();

    /**
     * Returns an executor that starts a virtual thread per task. Closing it waits for all of them to end.
     */
    ExecutorService newExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    }

    /**
     * Runs a task on a new thread of the executor under the given name.
     *
     * @throws java.util.concurrent.RejectedExecutionException if the executor is shut down
     */
    void execute(final ExecutorService threads, final String name, final Runnable task) {
        threads.execute(() -> {
            Thread.currentThread().setName(name);
            task.run();
        });
    }

    /**
     * Runs the session callbacks of {@code listener} on the current thread, marked as callbacks of that listener.
     */
    void runCallbacks(final SessionListener listener, final Runnable callbacks) {
        ScopedValue.where(CALLBACK_LISTENER, listener).run(callbacks);
    }

    /**
     * Returns the listener whose callbacks the current thread runs, or {@code null} outside a session thread.
     */
    SessionListener callbackListener() {
        return CALLBACK_LISTENER.isBound() ? CALLBACK_LISTENER.get() : null;
    }
}
