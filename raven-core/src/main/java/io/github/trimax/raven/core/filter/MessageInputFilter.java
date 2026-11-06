package io.github.trimax.raven.core.filter;

import java.io.ObjectInputFilter;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.trimax.raven.core.Message;

/**
 * Deserialization filter applied to every object read from the network.
 *
 * <p>Only {@link Message} subclasses, a small set of safe JDK value types (with the superclass and the JDK
 * internal classes they need on the wire) and enums are accepted.
 * Any other class (including classes that are typically used as deserialization gadgets) is rejected
 * before its constructor or {@code readObject} logic runs. Structural limits protect against
 * resource-exhaustion payloads; they apply per message, not per stream, so a long-lived connection is
 * never rejected just for having carried many messages.
 *
 * <p>One instance serves one stream and is used only by the thread reading it: call {@link #startMessage()}
 * before each top-level {@code readObject}.
 */
public final class MessageInputFilter implements ObjectInputFilter {

    /** Maximum nesting depth of one message's object graph. */
    static final long MAX_DEPTH = 64;

    /** Maximum number of object references in one message. */
    static final long MAX_REFERENCES = 100_000;

    /**
     * Maximum total number of array elements one message may declare, across all its arrays. Arrays are
     * allocated as soon as their length is read, before any element arrives, so this bounds the memory a peer
     * can make us allocate per message (a few megabytes) however the arrays are nested.
     */
    static final long MAX_ARRAY_ELEMENTS = 1 << 20;

    private static final Set<Class<?>> ALLOWED_JDK_TYPES = Set.of(
            String.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, Character.class, UUID.class, BigInteger.class, BigDecimal.class,
            Instant.class, Duration.class, LocalDate.class, LocalTime.class, LocalDateTime.class,
            ArrayList.class, LinkedList.class, HashMap.class, LinkedHashMap.class,
            HashSet.class, LinkedHashSet.class);

    /**
     * Element types of the internal arrays that the allowed collections allocate while reading themselves
     * ({@code Object[]} for lists, {@code Map.Entry[]} for maps and sets). Only allowed as array components:
     * the elements stored in them are still checked one by one.
     */
    private static final Set<Class<?>> COLLECTION_ARRAY_COMPONENTS = Set.of(Object.class, Map.Entry.class);

    /**
     * Classes the allowed types bring along on the wire without being values themselves: {@code Number}, the
     * abstract superclass of the boxed numbers and of {@code BigInteger}/{@code BigDecimal}, whose descriptor is
     * read before theirs and which cannot be instantiated itself.
     */
    private static final Set<Class<?>> ALLOWED_SUPERCLASSES = Set.of(Number.class);

    /**
     * Package-private JDK classes behind the allowed types: {@code java.time.Ser} carries every {@code java.time}
     * value; {@code java.util.CollSer} carries the immutable {@code List.of}/{@code Set.of}/{@code Map.of}
     * collections and resolves to one of the {@code ImmutableCollections} classes, which are checked as well
     * (their elements are still checked one by one). Matched by name because they are not accessible; nothing
     * outside the JDK can define a class in {@code java.*}.
     */
    private static final Set<String> ALLOWED_JDK_INTERNALS = Set.of(
            "java.time.Ser", "java.util.CollSer",
            "java.util.ImmutableCollections$List12", "java.util.ImmutableCollections$ListN",
            "java.util.ImmutableCollections$Set12", "java.util.ImmutableCollections$SetN",
            "java.util.ImmutableCollections$Map1", "java.util.ImmutableCollections$MapN");

    /** Stream reference count when the current message started, or -1 until its first object is checked. */
    private long referencesAtStart = -1;

    /** Array elements declared so far by the current message. */
    private long arrayElements;

    /**
     * Starts the limits for the next top-level message. Call before each {@code readObject}.
     */
    public void startMessage() {
        referencesAtStart = -1;
        arrayElements = 0;
    }

    @Override
    public Status checkInput(final FilterInfo info) {
        // The stream counts references from its very start, and the peer's reset() does not change that
        if (referencesAtStart < 0)
            referencesAtStart = info.references();

        if (info.depth() > MAX_DEPTH || info.references() - referencesAtStart > MAX_REFERENCES)
            return Status.REJECTED;

        if (info.arrayLength() > 0) {
            arrayElements += info.arrayLength();
            if (arrayElements > MAX_ARRAY_ELEMENTS)
                return Status.REJECTED;
        }

        final var type = info.serialClass();
        if (type == null)
            return Status.UNDECIDED;

        return isAllowed(type) ? Status.ALLOWED : Status.REJECTED;
    }

    private static boolean isAllowed(final Class<?> type) {
        var component = type;
        while (component.isArray())
            component = component.getComponentType();

        if (component.isPrimitive())
            return true;

        if (type.isArray() && COLLECTION_ARRAY_COMPONENTS.contains(component))
            return true;

        return Message.class.isAssignableFrom(component)
                || Enum.class.isAssignableFrom(component)
                || ALLOWED_JDK_TYPES.contains(component)
                || ALLOWED_SUPERCLASSES.contains(component)
                || ALLOWED_JDK_INTERNALS.contains(component.getName());
    }
}
