package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.NotNull;

class NotNullValidatorTest {

    private final NotNullValidator validator = new NotNullValidator();

    @NotNull
    @SuppressWarnings("unused")
    private static String annotatedField;

    @Test
    void validNonNullString() {
        assertNull(validator.validate("field", "hello", getAnnotation()));
    }

    @Test
    void validNonNullNumber() {
        assertNull(validator.validate("field", 42, getAnnotation()));
    }

    @Test
    void invalidNullValue() {
        final var result = validator.validate("field", null, getAnnotation());
        assertNotNull(result);
        assertEquals("NotNull", result.constraint());
        assertEquals("field", result.fieldName());
        assertEquals("field 'field' must not be null", result.message());
    }

    private NotNull getAnnotation() {
        try {
            return NotNullValidatorTest.class.getDeclaredField("annotatedField").getAnnotation(NotNull.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
