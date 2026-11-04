package io.github.trimax.raven.core.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.validation.annotation.NotBlank;
import io.github.trimax.raven.core.validation.annotation.Valid;

/**
 * Verifies that recursive validation terminates on cyclic object graphs without losing violations.
 */
class MessageValidatorCycleTest {

    static class Node extends Message {
        @Valid
        Node next;

        @NotBlank
        String name;

        Node(final String name) {
            this.name = name;
        }
    }

    static class Holder extends Message {
        @Valid
        List<Object> items = new ArrayList<>();
    }

    @Test
    void selfReferencingObjectTerminates() {
        final var node = new Node("");
        node.next = node;

        final var violations = assertDoesNotThrow(() -> MessageValidator.validate(node));

        assertEquals(1, violations.size());
    }

    @Test
    void cycleThroughTwoObjectsTerminates() {
        final var first = new Node("ok");
        final var second = new Node("");
        first.next = second;
        second.next = first;

        final var violations = assertDoesNotThrow(() -> MessageValidator.validate(first));

        assertEquals(1, violations.size());
    }

    @Test
    void collectionContainingItselfTerminates() {
        final var holder = new Holder();
        holder.items.add(holder.items);

        assertDoesNotThrow(() -> MessageValidator.validate(holder));
    }

    @Test
    void sharedNonCyclicReferenceIsValidatedOnEachPath() {
        final var shared = new Node("");
        final var holder = new Holder();
        holder.items.add(shared);
        holder.items.add(shared);

        final var violations = MessageValidator.validate(holder);

        assertEquals(2, violations.size());
    }
}
