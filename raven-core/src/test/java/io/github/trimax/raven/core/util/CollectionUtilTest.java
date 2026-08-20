package io.github.trimax.raven.core.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CollectionUtil}.
 */
class CollectionUtilTest {

    // --- isEmpty ---

    @Test
    void isEmptyReturnsTrueForNull() {
        assertTrue(CollectionUtil.isEmpty(null));
    }

    @Test
    void isEmptyReturnsTrueForEmptyList() {
        assertTrue(CollectionUtil.isEmpty(List.of()));
    }

    @Test
    void isEmptyReturnsTrueForEmptySet() {
        assertTrue(CollectionUtil.isEmpty(Set.of()));
    }

    @Test
    void isEmptyReturnsTrueForEmptyMutableList() {
        assertTrue(CollectionUtil.isEmpty(new ArrayList<>()));
    }

    @Test
    void isEmptyReturnsFalseForNonEmptyList() {
        assertFalse(CollectionUtil.isEmpty(List.of("a")));
    }

    @Test
    void isEmptyReturnsFalseForNonEmptySet() {
        assertFalse(CollectionUtil.isEmpty(Set.of(1, 2, 3)));
    }

    @Test
    void isEmptyReturnsTrueForCollectionsEmptyList() {
        assertTrue(CollectionUtil.isEmpty(Collections.emptyList()));
    }

    // --- isNotEmpty ---

    @Test
    void isNotEmptyReturnsFalseForNull() {
        assertFalse(CollectionUtil.isNotEmpty(null));
    }

    @Test
    void isNotEmptyReturnsFalseForEmptyList() {
        assertFalse(CollectionUtil.isNotEmpty(List.of()));
    }

    @Test
    void isNotEmptyReturnsTrueForNonEmptyList() {
        assertTrue(CollectionUtil.isNotEmpty(List.of("a", "b")));
    }

    @Test
    void isNotEmptyReturnsTrueForSingletonList() {
        assertTrue(CollectionUtil.isNotEmpty(Collections.singletonList("x")));
    }

    @Test
    void isNotEmptyReturnsTrueForNonEmptySet() {
        assertTrue(CollectionUtil.isNotEmpty(Set.of(42)));
    }
}
