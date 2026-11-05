package io.github.trimax.raven.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MessageReader}, on in-memory streams.
 */
class MessageReaderTest {

    /** Serializable, but neither a Message nor an allowed JDK type. */
    static final class Payload implements Serializable {
    }

    /** A message with the collection types the filter allows. */
    static final class CollectionsMessage extends Message {
        private final List<String> list = new ArrayList<>(List.of("a", "b"));
        private final Map<String, Integer> map = new HashMap<>(Map.of("one", 1, "two", 2));
        private final Set<UUID> set = new HashSet<>(Set.of(UUID.randomUUID()));
        private final Map<String, String> linkedMap = new LinkedHashMap<>(Map.of("k", "v"));
        private final Set<String> linkedSet = new LinkedHashSet<>(List.of("x", "y"));
    }

    /** A message with every allowed value type, boxed and as a JDK proxy, plus immutable collections. */
    static final class ValuesMessage extends Message {
        private final List<Object> values = new ArrayList<>(List.of("s", true, (byte) 1, (short) 2, 3, 4L, 5f, 6d, 'c',
                UUID.randomUUID(), BigInteger.TEN, new BigDecimal("1.5"), Instant.EPOCH, Duration.ofSeconds(7),
                LocalDate.EPOCH, LocalTime.NOON, LocalDateTime.of(2026, 1, 1, 0, 0), TimeUnit.SECONDS));
        private final List<Integer> immutableList = List.of(1, 2, 3);
        private final Map<String, Long> immutableMap = Map.of("a", 1L);
        private final Set<String> immutableSet = Set.of("x");
    }

    /** A message whose arrays together exceed the per-message element budget. */
    static final class BigArraysMessage extends Message {
        private final byte[][] chunks = new byte[2][1 << 20];
    }

    @Test
    void readsMessagesInOrder() throws Exception {
        final var reader = new MessageReader(stream(new TestMessage("first"), new TestMessage("second")));

        assertEquals("first", ((TestMessage) reader.read()).getContent());
        assertEquals("second", ((TestMessage) reader.read()).getContent());
    }

    @Test
    void readsMessagesWithAllowedCollections() throws Exception {
        final var sent = new CollectionsMessage();
        final var received = (CollectionsMessage) new MessageReader(stream(sent)).read();

        assertEquals(sent.list, received.list);
        assertEquals(sent.map, received.map);
        assertEquals(sent.set, received.set);
        assertEquals(sent.linkedMap, received.linkedMap);
        assertEquals(sent.linkedSet, received.linkedSet);
    }

    @Test
    void readsEveryAllowedValueType() throws Exception {
        final var sent = new ValuesMessage();
        final var received = (ValuesMessage) new MessageReader(stream(sent)).read();

        assertEquals(sent.values, received.values);
        assertEquals(sent.immutableList, received.immutableList);
        assertEquals(sent.immutableMap, received.immutableMap);
        assertEquals(sent.immutableSet, received.immutableSet);
    }

    @Test
    void keepsReadingOnALongSession() throws Exception {
        // Each message is small, but the stream's reference counter grows with every one of them
        final int messages = 60_000;
        final var bytes = new ByteArrayOutputStream();
        try (final var output = new ObjectOutputStream(bytes)) {
            for (int i = 0; i < messages; i++) {
                output.writeObject(new TestMessage("message " + i));
                output.reset();
            }
        }

        final var reader = new MessageReader(new ByteArrayInputStream(bytes.toByteArray()));
        for (int i = 0; i < messages; i++)
            assertEquals("message " + i, ((TestMessage) reader.read()).getContent());
    }

    @Test
    void rejectsMessagesWhoseArraysExceedTheElementBudget() throws Exception {
        final var reader = new MessageReader(stream(new BigArraysMessage()));

        assertThrows(InvalidClassException.class, reader::read);
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
