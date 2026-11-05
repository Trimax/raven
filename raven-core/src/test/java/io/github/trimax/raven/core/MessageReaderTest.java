package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MessageReader}, on in-memory streams.
 */
class MessageReaderTest {

    /** Serializable, but neither a Message nor an allowed JDK type. */
    static final class Payload implements Serializable {
    }

    @Test
    void readsMessagesInOrder() throws Exception {
        final var reader = new MessageReader(stream(new TestMessage("first"), new TestMessage("second")));

        assertEquals("first", ((TestMessage) reader.read()).getContent());
        assertEquals("second", ((TestMessage) reader.read()).getContent());
    }

    @Test
    void returnsNullForAnAllowedObjectThatIsNotAMessage() throws Exception {
        final var reader = new MessageReader(stream("I am not a Message"));

        assertNull(reader.read());
    }

    @Test
    void rejectsClassesOutsideTheFilter() throws Exception {
        final var reader = new MessageReader(stream(new Payload()));

        assertThrows(InvalidClassException.class, reader::read);
    }

    @Test
    void reportsEndOfStream() throws Exception {
        final var reader = new MessageReader(stream());

        assertThrows(EOFException.class, reader::read);
    }

    @Test
    void failsWithoutTheStreamHeader() {
        assertThrows(IOException.class, () -> new MessageReader(new ByteArrayInputStream(new byte[0])));
    }

    private static ByteArrayInputStream stream(final Object... objects) throws IOException {
        final var bytes = new ByteArrayOutputStream();
        try (final var output = new ObjectOutputStream(bytes)) {
            for (final var object : objects)
                output.writeObject(object);
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
