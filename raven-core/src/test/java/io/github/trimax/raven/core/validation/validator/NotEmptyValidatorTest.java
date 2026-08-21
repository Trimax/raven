package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.NotEmpty;

class NotEmptyValidatorTest {

    private final NotEmptyValidator validator = new NotEmptyValidator();

    @NotEmpty
    @SuppressWarnings("unused")
    private static String annotatedField;

    @Test
    void validNonEmptyString() {
        final var result = validator.validate("field", "hello", getAnnotation());
        assertNull(result);
    }

    @Test
    void validNonEmptyCollection() {
        final var result = validator.validate("field", List.of("a", "b"), getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidNullValue() {
        final var result = validator.validate("field", null, getAnnotation());
        assertNotNull(result);
        assertEquals("NotEmpty", result.constraint());
        assertEquals("field", result.fieldName());
    }

    @Test
    void invalidEmptyString() {
        final var result = validator.validate("field", "", getAnnotation());
        assertNotNull(result);
        assertEquals("NotEmpty", result.constraint());
    }

    @Test
    void invalidEmptyCollection() {
        final var result = validator.validate("field", Collections.emptyList(), getAnnotation());
        assertNotNull(result);
        assertEquals("NotEmpty", result.constraint());
    }

    private NotEmpty getAnnotation() {
        try {
            return NotEmptyValidatorTest.class.getDeclaredField("annotatedField").getAnnotation(NotEmpty.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
