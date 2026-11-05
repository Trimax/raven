package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.Length;

class LengthValidatorTest {

    private final LengthValidator validator = new LengthValidator();

    @Length(min = 3, max = 10)
    @SuppressWarnings("unused")
    private static String lengthField;

    @Length(min = 3)
    @SuppressWarnings("unused")
    private static String minOnlyField;

    @Test
    void validLengthWithinRange() {
        final var result = validator.validate("name", "hello", getAnnotation());
        assertNull(result);
    }

    @Test
    void validLengthAtMinBoundary() {
        final var result = validator.validate("name", "abc", getAnnotation());
        assertNull(result);
    }

    @Test
    void validLengthAtMaxBoundary() {
        final var result = validator.validate("name", "abcdefghij", getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidLengthTooShort() {
        final var result = validator.validate("name", "ab", getAnnotation());
        assertNotNull(result);
        assertEquals("Length", result.constraint());
        assertEquals("name", result.fieldName());
    }

    @Test
    void invalidLengthTooLong() {
        final var result = validator.validate("name", "abcdefghijk", getAnnotation());
        assertNotNull(result);
        assertEquals("Length", result.constraint());
    }

    @Test
    void defaultMaxAcceptsLongStrings() throws NoSuchFieldException {
        // The default max is Long.MAX_VALUE; it used to be cast to int (-1) and reject every string
        final var minOnly = LengthValidatorTest.class.getDeclaredField("minOnlyField").getAnnotation(Length.class);

        assertNull(validator.validate("name", "abc", minOnly));
        assertNull(validator.validate("name", "x".repeat(10_000), minOnly));
        assertNotNull(validator.validate("name", "ab", minOnly));
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("name", null, getAnnotation()));
    }

    private Length getAnnotation() {
        try {
            return LengthValidatorTest.class.getDeclaredField("lengthField").getAnnotation(Length.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
