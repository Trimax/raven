package io.github.trimax.raven.core.util;

import java.util.Collection;

import lombok.experimental.UtilityClass;

/**
 * Null-safe collection utility methods.
 */
@UtilityClass
public final class CollectionUtil {

    /**
     * Returns {@code true} if the collection is null or contains no elements.
     */
    public boolean isEmpty(final Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Returns {@code true} if the collection is not null and contains at least one element.
     */
    public boolean isNotEmpty(final Collection<?> collection) {
        return !isEmpty(collection);
    }
}
