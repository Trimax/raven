package io.github.trimax.raven.core.codec;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.TestMessage;

/**
 * Unit tests for {@link JavaSerializationCodec}: round trips of every allowed type and the deserialization filter.
 */
class JavaSerializationCodecTest {

    private final JavaSerializationCodec codec = new JavaSerializationCodec();

    /** Serializable, but neither a Message nor an allowed JDK type. */
    static final class Payload implements Serializable {
    }

    /** A message with the mutable collection types the filter allows. */
    static final class CollectionsMessage extends Message {
        private final List<String> list = new ArrayList<>(List.of("a", "b"));
        private final List<String> linkedList = new LinkedList<>(List.of("c"));
        private final Map<String, Integer> map = new HashMap<>(Map.of("one", 1, "two", 2));
        private final Set<UUID> set = new HashSet<>(Set.of(UUID.randomUUID()));
        private final Map<String, String> linkedMap = new LinkedHashMap<>(Map.of("k", "v"));
        private final Set<String> linkedSet = new LinkedHashSet<>(List.of("x", "y"));
    }

    /** A message with every allowed value type, boxed and as a JDK proxy, plus immutable collections and arrays. */
    static final class ValuesMessage extends Message {
        private final List<Object> values = new ArrayList<>(List.of("s", true, (byte) 1, (short) 2, 3, 4L, 5f, 6d, 'c',
                UUID.randomUUID(), BigInteger.TEN, new BigDecimal("1.5"), Instant.EPOCH, Duration.ofSeconds(7),
                LocalDate.EPOCH, LocalTime.NOON, LocalDateTime.of(2026, 1, 1, 0, 0), TimeUnit.SECONDS));
        private final List<Integer> immutableList = List.of(1, 2, 3);
        private final List<Integer> immutableListN = List.of(1, 2, 3, 4, 5);
        private final Map<String, Long> immutableMap = Map.of("a", 1L);
        private final Map<String, Long> immutableMapN = Map.of("a", 1L, "b", 2L, "c", 3L);
        private final Set<String> immutableSet = Set.of("x");
        private final Set<String> immutableSetN = Set.of("x", "y", "z");
        private final int[] numbers = {1, 2, 3};
        private final String[] names = {"a", "b"};
    }

    /** A message whose arrays together exceed the per-message element budget. */
    static final class BigArraysMessage extends Message {
        private final byte[][] chunks = new byte[2][1 << 20];
    }

    /** A message with an array that uses up almost the whole element budget on its own. */
    static final class AlmostFullMessage extends Message {
        private final byte[] data = new byte[(1 << 20) - 16];
    }

    /** A message with a field the codec cannot serialize. */
    static final class UnserializableMessage extends Message {
        @SuppressWarnings({"unused", "serial"})
        private final Object notSerializable = new Object();
    }

    @Test
    void roundTripsAMessage() throws IOException {
        final var received = codec.decode(codec.encode(new TestMessage("hello")));

        assertEquals("hello", assertInstanceOf(TestMessage.class, received).getContent());
    }

    @Test
    void roundTripsTheAllowedCollections() throws IOException {
        final var sent = new CollectionsMessage();
        final var received = (CollectionsMessage) codec.decode(codec.encode(sent));

        assertEquals(sent.list, received.list);
        assertEquals(sent.linkedList, received.linkedList);
        assertEquals(sent.map, received.map);
        assertEquals(sent.set, received.set);
        assertEquals(sent.linkedMap, received.linkedMap);
        assertEquals(sent.linkedSet, received.linkedSet);
    }

    @Test
    void roundTripsEveryAllowedValueType() throws IOException {
        final var sent = new ValuesMessage();
        final var received = (ValuesMessage) codec.decode(codec.encode(sent));

        assertEquals(sent.values, received.values);
        assertEquals(sent.immutableList, received.immutableList);
        assertEquals(sent.immutableListN, received.immutableListN);
        assertEquals(sent.immutableMap, received.immutableMap);
        assertEquals(sent.immutableMapN, received.immutableMapN);
        assertEquals(sent.immutableSet, received.immutableSet);
        assertEquals(sent.immutableSetN, received.immutableSetN);
        assertArrayEquals(sent.numbers, received.numbers);
        assertArrayEquals(sent.names, received.names);
    }

    @Test
    void rejectsClassesOutsideTheFilter() throws IOException {
        assertThrows(InvalidClassException.class, () -> codec.decode(serialize(new Payload())));
    }

    @Test
    void rejectsMessagesWhoseArraysExceedTheElementBudget() throws IOException {
        final var frame = codec.encode(new BigArraysMessage());

        assertThrows(InvalidClassException.class, () -> codec.decode(frame));
    }

    @Test
    void limitsApplyPerMessage() throws IOException {
        // Each message nearly exhausts the array budget, so a budget shared between messages would reject the second
        final var frame = codec.encode(new AlmostFullMessage());

        assertInstanceOf(AlmostFullMessage.class, codec.decode(frame));
        assertInstanceOf(AlmostFullMessage.class, codec.decode(frame));
    }

    @Test
    void returnsNullForAnAllowedObjectThatIsNotAMessage() throws IOException {
        assertNull(codec.decode(serialize("I am not a Message")));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(StreamCorruptedException.class, () -> codec.decode(new byte[]{1, 2, 3, 4, 5}));
    }

    @Test
    void reportsAnUnserializableMessage() {
        assertThrows(NotSerializableException.class, () -> codec.encode(new UnserializableMessage()));
    }

    private static byte[] serialize(final Object object) throws IOException {
        final var bytes = new ByteArrayOutputStream();
        try (final var output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }
}
