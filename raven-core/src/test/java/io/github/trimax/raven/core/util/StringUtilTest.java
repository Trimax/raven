package io.github.trimax.raven.core.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StringUtil}.
 */
class StringUtilTest {

    // --- isEmpty ---

    @Test
    void isEmptyReturnsTrueForNull() {
        assertTrue(StringUtil.isEmpty(null));
    }

    @Test
    void isEmptyReturnsTrueForEmptyString() {
        assertTrue(StringUtil.isEmpty(""));
    }

    @Test
    void isEmptyReturnsFalseForWhitespace() {
        assertFalse(StringUtil.isEmpty("  "));
    }

    @Test
    void isEmptyReturnsFalseForNonEmptyString() {
        assertFalse(StringUtil.isEmpty("hello"));
    }

    // --- defaultIfEmpty ---

    @Test
    void defaultIfEmptyReturnsDefaultForNull() {
        assertEquals("default", StringUtil.defaultIfEmpty(null, "default"));
    }

    @Test
    void defaultIfEmptyReturnsDefaultForEmptyString() {
        assertEquals("default", StringUtil.defaultIfEmpty("", "default"));
    }

    @Test
    void defaultIfEmptyReturnsOriginalForWhitespace() {
        assertEquals("  ", StringUtil.defaultIfEmpty("  ", "default"));
    }

    @Test
    void defaultIfEmptyReturnsOriginalForNonEmptyString() {
        assertEquals("hello", StringUtil.defaultIfEmpty("hello", "default"));
    }

    // --- isNotEmpty ---

    @Test
    void isNotEmptyReturnsFalseForNull() {
        assertFalse(StringUtil.isNotEmpty(null));
    }

    @Test
    void isNotEmptyReturnsFalseForEmptyString() {
        assertFalse(StringUtil.isNotEmpty(""));
    }

    @Test
    void isNotEmptyReturnsTrueForWhitespace() {
        assertTrue(StringUtil.isNotEmpty("  "));
    }

    @Test
    void isNotEmptyReturnsTrueForNonEmptyString() {
        assertTrue(StringUtil.isNotEmpty("hello"));
    }

    // --- isBlank ---

    @Test
    void isBlankReturnsTrueForNull() {
        assertTrue(StringUtil.isBlank(null));
    }

    @Test
    void isBlankReturnsTrueForEmptyString() {
        assertTrue(StringUtil.isBlank(""));
    }

    @Test
    void isBlankReturnsTrueForWhitespace() {
        assertTrue(StringUtil.isBlank("   "));
    }

    @Test
    void isBlankReturnsTrueForTabs() {
        assertTrue(StringUtil.isBlank("\t\n\r"));
    }

    @Test
    void isBlankReturnsFalseForNonBlankString() {
        assertFalse(StringUtil.isBlank("text"));
    }

    @Test
    void isBlankReturnsFalseForStringWithLeadingWhitespace() {
        assertFalse(StringUtil.isBlank("  text"));
    }

    // --- defaultIfBlank ---

    @Test
    void defaultIfBlankReturnsDefaultForNull() {
        assertEquals("default", StringUtil.defaultIfBlank(null, "default"));
    }

    @Test
    void defaultIfBlankReturnsDefaultForEmptyString() {
        assertEquals("default", StringUtil.defaultIfBlank("", "default"));
    }

    @Test
    void defaultIfBlankReturnsDefaultForWhitespace() {
        assertEquals("default", StringUtil.defaultIfBlank("   ", "default"));
    }

    @Test
    void defaultIfBlankReturnsOriginalForNonBlankString() {
        assertEquals("hello", StringUtil.defaultIfBlank("hello", "default"));
    }

    @Test
    void defaultIfBlankReturnsOriginalForStringWithLeadingWhitespace() {
        assertEquals("  text", StringUtil.defaultIfBlank("  text", "default"));
    }

    // --- isNotBlank ---

    @Test
    void isNotBlankReturnsFalseForNull() {
        assertFalse(StringUtil.isNotBlank(null));
    }

    @Test
    void isNotBlankReturnsFalseForEmptyString() {
        assertFalse(StringUtil.isNotBlank(""));
    }

    @Test
    void isNotBlankReturnsFalseForWhitespace() {
        assertFalse(StringUtil.isNotBlank("   "));
    }

    @Test
    void isNotBlankReturnsTrueForNonBlankString() {
        assertTrue(StringUtil.isNotBlank("text"));
    }

    @Test
    void isNotBlankReturnsTrueForStringWithTrailingWhitespace() {
        assertTrue(StringUtil.isNotBlank("text  "));
    }

    // --- isLongerThan ---

    @Test
    void isLongerThanReturnsFalseForNull() {
        assertFalse(StringUtil.isLongerThan(null, 3));
    }

    @Test
    void isLongerThanReturnsTrueWhenLonger() {
        assertTrue(StringUtil.isLongerThan("hello", 3));
    }

    @Test
    void isLongerThanReturnsFalseForExactLength() {
        assertFalse(StringUtil.isLongerThan("abc", 3));
    }

    @Test
    void isLongerThanReturnsFalseWhenShorter() {
        assertFalse(StringUtil.isLongerThan("ab", 3));
    }

    // --- isShorterThan ---

    @Test
    void isShorterThanReturnsFalseForNull() {
        assertFalse(StringUtil.isShorterThan(null, 3));
    }

    @Test
    void isShorterThanReturnsTrueWhenShorter() {
        assertTrue(StringUtil.isShorterThan("ab", 3));
    }

    @Test
    void isShorterThanReturnsFalseForExactLength() {
        assertFalse(StringUtil.isShorterThan("abc", 3));
    }

    @Test
    void isShorterThanReturnsFalseWhenLonger() {
        assertFalse(StringUtil.isShorterThan("hello", 3));
    }

    // --- hasLengthBetween ---

    @Test
    void hasLengthBetweenReturnsFalseForNull() {
        assertFalse(StringUtil.hasLengthBetween(null, 1, 5));
    }

    @Test
    void hasLengthBetweenReturnsTrueForMinBoundary() {
        assertTrue(StringUtil.hasLengthBetween("ab", 2, 5));
    }

    @Test
    void hasLengthBetweenReturnsTrueForMaxBoundary() {
        assertTrue(StringUtil.hasLengthBetween("hello", 2, 5));
    }

    @Test
    void hasLengthBetweenReturnsTrueForMiddleValue() {
        assertTrue(StringUtil.hasLengthBetween("abc", 2, 5));
    }

    @Test
    void hasLengthBetweenReturnsFalseWhenTooShort() {
        assertFalse(StringUtil.hasLengthBetween("a", 2, 5));
    }

    @Test
    void hasLengthBetweenReturnsFalseWhenTooLong() {
        assertFalse(StringUtil.hasLengthBetween("too long", 2, 5));
    }

    // --- strip ---

    @Test
    void stripReturnsNullForNull() {
        assertNull(StringUtil.strip(null));
    }

    @Test
    void stripReturnsEmptyForEmptyString() {
        assertEquals("", StringUtil.strip(""));
    }

    @Test
    void stripRemovesLeadingAndTrailingWhitespace() {
        assertEquals("hello", StringUtil.strip("  hello  "));
    }

    @Test
    void stripReturnsEmptyForWhitespaceOnly() {
        assertEquals("", StringUtil.strip("   "));
    }

    @Test
    void stripPreservesInternalWhitespace() {
        assertEquals("hello world", StringUtil.strip("  hello world  "));
    }

    @Test
    void stripReturnsOriginalWhenNoWhitespace() {
        assertEquals("hello", StringUtil.strip("hello"));
    }

    @Test
    void stripRemovesUnicodeWhitespace() {
        assertEquals("hello", StringUtil.strip("\u2003hello\u2003"));
    }
}
