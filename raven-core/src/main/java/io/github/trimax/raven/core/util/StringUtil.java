package io.github.trimax.raven.core.util;

import lombok.experimental.UtilityClass;

/**
 * Null-safe string utility methods.
 */
@UtilityClass
public final class StringUtil {

    /**
     * Returns {@code true} if the string is null or empty (length 0).
     */
    public boolean isEmpty(final String value) {
        return value == null || value.isEmpty();
    }

    /**
     * Returns {@code true} if the string is not null and not empty.
     */
    public boolean isNotEmpty(final String value) {
        return !isEmpty(value);
    }

    /**
     * Returns {@code true} if the string is null, empty, or contains only whitespace.
     */
    public boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    /**
     * Returns {@code true} if the string is not null and contains at least one non-whitespace character.
     */
    public boolean isNotBlank(final String value) {
        return !isBlank(value);
    }

    /**
     * Returns {@code true} if the string is not null and has more than {@code length} characters.
     */
    public boolean isLongerThan(final String value, final int length) {
        return value != null && value.length() > length;
    }

    /**
     * Returns {@code true} if the string is not null and has fewer than {@code length} characters.
     */
    public boolean isShorterThan(final String value, final int length) {
        return value != null && value.length() < length;
    }

    /**
     * Returns {@code true} if the string is not null and its length is between
     * {@code min} and {@code max} inclusive.
     */
    public boolean hasLengthBetween(final String value, final int min, final int max) {
        return value != null && value.length() >= min && value.length() <= max;
    }

    /**
     * Returns a stripped copy of the string (all leading/trailing Unicode whitespace removed),
     * or {@code null} if the input is null.
     */
    public String strip(final String value) {
        return value == null ? null : value.strip();
    }
}
