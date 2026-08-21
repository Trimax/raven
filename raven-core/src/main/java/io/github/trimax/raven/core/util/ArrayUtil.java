package io.github.trimax.raven.core.util;

import lombok.experimental.UtilityClass;

/**
 * Null-safe array utility methods.
 */
@UtilityClass
public final class ArrayUtil {

    /**
     * Returns {@code true} if the array is null or has zero length.
     */
    public <T> boolean isEmpty(final T[] array) {
        return array == null || array.length == 0;
    }
}
