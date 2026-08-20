package io.github.trimax.raven.core.util.interfaces;

/**
 * A functional interface similar to {@link Runnable} but capable of throwing checked exceptions.
 *
 * @param <E> the type of exception that may be thrown
 */
@FunctionalInterface
public interface ThrowingRunnable<E extends Exception> {

    /**
     * Executes this runnable operation.
     *
     * @throws E if an error occurs during execution
     */
    void run() throws E;
}
