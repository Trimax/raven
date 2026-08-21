package io.github.trimax.raven.core.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ArrayUtil}.
 */
class ArrayUtilTest {

    @Test
    void isEmptyReturnsTrueForNull() {
        assertTrue(ArrayUtil.isEmpty(null));
    }

    @Test
    void isEmptyReturnsTrueForEmptyArray() {
        assertTrue(ArrayUtil.isEmpty(new String[0]));
    }

    @Test
    void isEmptyReturnsFalseForSingleElement() {
        assertFalse(ArrayUtil.isEmpty(new String[]{"a"}));
    }

    @Test
    void isEmptyReturnsFalseForMultipleElements() {
        assertFalse(ArrayUtil.isEmpty(new Integer[]{1, 2, 3}));
    }

    @Test
    void isEmptyReturnsTrueForEmptyObjectArray() {
        assertTrue(ArrayUtil.isEmpty(new Object[0]));
    }
}
