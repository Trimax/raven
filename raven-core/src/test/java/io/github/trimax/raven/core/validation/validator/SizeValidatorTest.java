package io.github.trimax.raven.core.validation.validator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.validation.annotation.Size;

class SizeValidatorTest {

    private final SizeValidator validator = new SizeValidator();

    @Size(min = 1, max = 3)
    @SuppressWarnings("unused")
    private static List<String> sizeField;

    @Test
    void validSizeWithinRange() {
        final var result = validator.validate("items", List.of("a", "b"), getAnnotation());
        assertNull(result);
    }

    @Test
    void validSizeAtMinBoundary() {
        final var result = validator.validate("items", List.of("a"), getAnnotation());
        assertNull(result);
    }

    @Test
    void validSizeAtMaxBoundary() {
        final var result = validator.validate("items", List.of("a", "b", "c"), getAnnotation());
        assertNull(result);
    }

    @Test
    void invalidSizeTooSmall() {
        final var result = validator.validate("items", Collections.emptyList(), getAnnotation());
        assertNotNull(result);
        assertEquals("Size", result.constraint());
        assertEquals("items", result.fieldName());
    }

    @Test
    void invalidSizeTooLarge() {
        final var result = validator.validate("items", List.of("a", "b", "c", "d"), getAnnotation());
        assertNotNull(result);
        assertEquals("Size", result.constraint());
    }

    @Test
    void nullValueSkipped() {
        assertNull(validator.validate("items", null, getAnnotation()));
    }

    private Size getAnnotation() {
        try {
            return SizeValidatorTest.class.getDeclaredField("sizeField").getAnnotation(Size.class);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
