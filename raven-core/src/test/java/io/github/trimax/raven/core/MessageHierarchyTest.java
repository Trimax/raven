package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.exception.MessageValidationRavenException;
import io.github.trimax.raven.core.validation.MessageValidator;
import io.github.trimax.raven.core.validation.Violation;
import io.github.trimax.raven.core.validation.annotation.Length;
import io.github.trimax.raven.core.validation.annotation.Min;
import io.github.trimax.raven.core.validation.annotation.NotBlank;
import io.github.trimax.raven.core.validation.annotation.NotNull;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Validation and serialization of messages with an abstract intermediate class
 * declared with {@code @SuperBuilder} and {@code final} fields.
 */
class MessageHierarchyTest {

    @Test
    void validationChecksFieldsDeclaredInAbstractSuperclass() {
        final var message = CreateRequest.builder()
                .requestId(" ")
                .userId(null)
                .name("alpha")
                .quantity(1)
                .build();

        final var fields = MessageValidator.validate(message).stream()
                .map(Violation::fieldName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("requestId", "userId"), fields);
    }

    @Test
    void validationChecksBothSuperclassAndSubclassFields() {
        final var message = CreateRequest.builder()
                .requestId("x".repeat(65))
                .userId(UUID.randomUUID())
                .name("")
                .quantity(0)
                .build();

        final var fields = MessageValidator.validate(message).stream()
                .map(Violation::fieldName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("requestId", "name", "quantity"), fields);
    }

    @Test
    void validateOrThrowRejectsInvalidSuperclassField() {
        final var message = CreateRequest.builder()
                .requestId("")
                .userId(UUID.randomUUID())
                .name("alpha")
                .quantity(1)
                .build();

        final var ex = assertThrows(MessageValidationRavenException.class,
                () -> MessageValidator.validateOrThrow(message));
        assertEquals(List.of("requestId"), ex.getViolations().stream().map(Violation::fieldName).distinct().toList());
    }

    @Test
    void validMessagePassesValidation() {
        final var message = validCreateRequest();

        assertDoesNotThrow(() -> MessageValidator.validateOrThrow(message));
    }

    @Test
    void roundTripPreservesSuperclassAndSubclassFields() throws Exception {
        final var original = validCreateRequest();

        final var restored = roundTrip(original);

        final var create = assertInstanceOf(CreateRequest.class, restored);
        assertEquals(original.getId(), create.getId());
        assertEquals(original.getTimestamp(), create.getTimestamp());
        assertEquals(original.getRequestId(), create.getRequestId());
        assertEquals(original.getUserId(), create.getUserId());
        assertEquals(original.getName(), create.getName());
        assertEquals(original.getQuantity(), create.getQuantity());
    }

    @Test
    void roundTripKeepsConcreteTypeAmongSiblings() throws Exception {
        final List<Message> messages = List.of(
                validCreateRequest(),
                DeleteRequest.builder().requestId("req-1").userId(UUID.randomUUID()).reason("obsolete").build());

        final var restored = List.of(roundTrip(messages.get(0)), roundTrip(messages.get(1)));

        assertInstanceOf(CreateRequest.class, restored.get(0));
        final var delete = assertInstanceOf(DeleteRequest.class, restored.get(1));
        assertEquals("obsolete", delete.getReason());
        assertEquals("req-1", delete.getRequestId());
    }

    @Test
    void roundTripOfToBuilderCopy() throws Exception {
        final var copy = validCreateRequest().toBuilder().requestId("req-2").build();

        final var restored = assertInstanceOf(CreateRequest.class, roundTrip(copy));

        assertEquals("req-2", restored.getRequestId());
        assertDoesNotThrow(() -> MessageValidator.validateOrThrow(restored));
    }

    private static CreateRequest validCreateRequest() {
        return CreateRequest.builder()
                .requestId("req-1")
                .userId(UUID.randomUUID())
                .name("alpha")
                .quantity(3)
                .build();
    }

    private static Message roundTrip(final Message message) throws IOException, ClassNotFoundException {
        final var bytes = new ByteArrayOutputStream();
        try (final var out = new ObjectOutputStream(bytes)) {
            out.writeObject(message);
        }

        try (final var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Message) in.readObject();
        }
    }

    // --- Messages ---

    @Getter
    @SuperBuilder(toBuilder = true)
    abstract static class BaseRequest extends Message {

        @Serial
        private static final long serialVersionUID = 1L;

        @NotBlank
        @Length(min = 1, max = 64)
        private final String requestId;

        @NotNull
        private final UUID userId;
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    static final class CreateRequest extends BaseRequest {

        @Serial
        private static final long serialVersionUID = 1L;

        @NotBlank
        private final String name;

        @Min(1)
        private final int quantity;
    }

    @Getter
    @SuperBuilder(toBuilder = true)
    static final class DeleteRequest extends BaseRequest {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String reason;
    }
}
