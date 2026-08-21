package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.Matches;

class MatchesValidatorTest {

    private final MatchesValidator validator = new MatchesValidator();

    @Matches("^[a-z]+$")
    @SuppressWarnings("unused")
    private static String matchesField;

    @Test
    void validMatchingString() {
        final var result = validator.validate("code", "abc", getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidNonMatchingString() {
        final var result = validator.validate("code", "ABC123", getAnnotation());
        assertNotNull(result);
        assertEquals("Matches", result.constraint());
        assertEquals("code", result.fieldName());
    }

    @Test
    void invalidEmptyString() {
        final var result = validator.validate("code", "", getAnnotation());
        assertNotNull(result);
        assertEquals("Matches", result.constraint());
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("code", null, getAnnotation()));
    }

    private Matches getAnnotation() {
        try {
            return MatchesValidatorTest.class.getDeclaredField("matchesField").getAnnotation(Matches.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
