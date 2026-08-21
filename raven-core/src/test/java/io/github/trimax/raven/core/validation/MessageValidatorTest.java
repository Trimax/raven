package io.github.trimax.raven.core.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.exception.MessageValidationRavenException;
import io.github.trimax.raven.core.validation.annotation.Email;
import io.github.trimax.raven.core.validation.annotation.Length;
import io.github.trimax.raven.core.validation.annotation.Min;
import io.github.trimax.raven.core.validation.annotation.NotBlank;
import io.github.trimax.raven.core.validation.annotation.NotEmpty;
import io.github.trimax.raven.core.validation.annotation.NotNull;
import io.github.trimax.raven.core.validation.annotation.Range;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

class MessageValidatorTest {

    @NoArgsConstructor
    @AllArgsConstructor
    private static class AnnotatedMessage extends Message {

        @NotBlank
        @Length(min = 3, max = 32)
        @SuppressWarnings("unused")
        private String username;

        @Email
        @NotNull
        @SuppressWarnings("unused")
        private String email;

        @Range(min = 1, max = 100)
        @SuppressWarnings("unused")
        private Integer level;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    private static class NullableFieldMessage extends Message {

        @Length(min = 3, max = 32)
        @SuppressWarnings("unused")
        private String optionalName;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    private static class ParentMessage extends Message {

        @NotBlank
        @SuppressWarnings("unused")
        private String parentField;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    private static class ChildMessage extends ParentMessage {

        @Min(1)
        @SuppressWarnings("unused")
        private int childField;
    }

    @NoArgsConstructor
    private static class PlainMessage extends Message {

        @SuppressWarnings("unused")
        private String noConstraints;
    }

    @Test
    void validateReturnsViolationsForInvalidFields() {
        final var message = new AnnotatedMessage("", null, 0);

        final List<Violation> violations = MessageValidator.validate(message);

        assertTrue(violations.size() >= 3);
        assertTrue(violations.stream().anyMatch(v -> "username".equals(v.fieldName()) && "NotBlank".equals(v.constraint())));
        assertTrue(violations.stream().anyMatch(v -> "email".equals(v.fieldName()) && "NotNull".equals(v.constraint())));
        assertTrue(violations.stream().anyMatch(v -> "level".equals(v.fieldName()) && "Range".equals(v.constraint())));
    }

    @Test
    void validateReturnsNoViolationsForValidMessage() {
        final var message = new AnnotatedMessage("validUser", "user@example.com", 50);

        final List<Violation> violations = MessageValidator.validate(message);

        assertEquals(0, violations.size());
    }

    @Test
    void nullFieldWithoutNotNullProducesNoViolation() {
        final var message = new NullableFieldMessage(null);

        final List<Violation> violations = MessageValidator.validate(message);

        assertEquals(0, violations.size());
    }

    @Test
    void validateOrThrowThrowsExceptionWithCorrectViolations() {
        final var message = new AnnotatedMessage("", null, 0);

        final var exception = assertThrows(
            MessageValidationRavenException.class,
            () -> MessageValidator.validateOrThrow(message)
        );

        assertNotNull(exception.getViolations());
        assertFalse(exception.getViolations().isEmpty());
        assertEquals(AnnotatedMessage.class, exception.getMessageType());
        assertTrue(exception.getMessage().contains("AnnotatedMessage"));
    }

    @Test
    void validateOrThrowDoesNotThrowForValidMessage() {
        final var message = new AnnotatedMessage("validUser", "user@example.com", 50);

        assertDoesNotThrow(() -> MessageValidator.validateOrThrow(message));
    }

    @Test
    void inheritanceValidatesParentClassFields() {
        final var message = new ChildMessage(0);

        final List<Violation> violations = MessageValidator.validate(message);

        assertTrue(violations.stream().anyMatch(v -> "parentField".equals(v.fieldName()) && "NotBlank".equals(v.constraint())));
        assertTrue(violations.stream().anyMatch(v -> "childField".equals(v.fieldName()) && "Min".equals(v.constraint())));
    }

    @Test
    void messageWithNoAnnotationsProducesNoViolations() {
        final var message = new PlainMessage();

        final List<Violation> violations = MessageValidator.validate(message);

        assertEquals(0, violations.size());
        assertDoesNotThrow(() -> MessageValidator.validateOrThrow(message));
    }

    @Test
    void cachingDoesNotCauseErrors() {
        final var message1 = new AnnotatedMessage("user1", "a@b.com", 10);
        final var message2 = new AnnotatedMessage("user2", "c@d.com", 20);

        assertDoesNotThrow(() -> MessageValidator.validate(message1));
        assertDoesNotThrow(() -> MessageValidator.validate(message2));

        assertEquals(0, MessageValidator.validate(message1).size());
        assertEquals(0, MessageValidator.validate(message2).size());
    }

    // --- Type compatibility tests ---

    @NoArgsConstructor
    private static class IncompatibleAnnotationMessage extends Message {

        @NotBlank
        @SuppressWarnings("unused")
        private int notAString;
    }

    @Test
    void incompatibleFieldTypeThrowsIllegalStateException() {
        final var message = new IncompatibleAnnotationMessage();

        final var exception = assertThrows(
            IllegalStateException.class,
            () -> MessageValidator.validate(message)
        );

        assertTrue(exception.getMessage().contains("NotBlank"));
        assertTrue(exception.getMessage().contains("notAString"));
        assertTrue(exception.getMessage().contains("int"));
    }

    // --- Array support tests ---

    @NoArgsConstructor
    @AllArgsConstructor
    private static class ArrayMessage extends Message {

        @NotEmpty
        @SuppressWarnings("unused")
        private String[] tags;

        @NotEmpty
        @SuppressWarnings("unused")
        private int[] scores;
    }

    @Test
    void notEmptyValidForNonEmptyObjectArray() {
        final var message = new ArrayMessage(new String[]{"tag1"}, new int[]{1, 2});

        final List<Violation> violations = MessageValidator.validate(message);

        assertEquals(0, violations.size());
    }

    @Test
    void notEmptyInvalidForEmptyObjectArray() {
        final var message = new ArrayMessage(new String[0], new int[]{1});

        final List<Violation> violations = MessageValidator.validate(message);

        assertEquals(1, violations.size());
        assertEquals("tags", violations.getFirst().fieldName());
        assertEquals("NotEmpty", violations.getFirst().constraint());
    }

    @Test
    void notEmptyInvalidForEmptyPrimitiveArray() {
        final var message = new ArrayMessage(new String[]{"ok"}, new int[0]);

        final List<Violation> violations = MessageValidator.validate(message);

        assertEquals(1, violations.size());
        assertEquals("scores", violations.getFirst().fieldName());
    }

    @Test
    void notEmptyInvalidForNullArray() {
        final var message = new ArrayMessage(null, null);

        final List<Violation> violations = MessageValidator.validate(message);

        assertEquals(2, violations.size());
    }
}
