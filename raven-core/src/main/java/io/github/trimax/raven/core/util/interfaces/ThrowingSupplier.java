package io.github.trimax.raven.core.util.interfaces;

/**
 * A functional interface similar to {@link java.util.function.Supplier} but capable of throwing checked exceptions.
 *
 * @param <T> the type of result supplied
 * @param <E> the type of exception that may be thrown
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception> {

    /**
     * Gets a result, potentially throwing a checked exception.
     *
     * @return a result
     * @throws E if an error occurs during execution
     */
    T get() throws E;
}
