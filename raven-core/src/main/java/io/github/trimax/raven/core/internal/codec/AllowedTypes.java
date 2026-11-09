package io.github.trimax.raven.core.internal.codec;

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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The types a message may always contain, whatever packages are trusted: {@link Message} subclasses, records, enums,
 * primitives, a small set of safe JDK value types (with the superclass and the JDK internal classes they need on the
 * wire) and arrays of all of these.
 *
 * <p>Records are safe to allow: the JVM rebuilds a record only through its canonical constructor and never runs a
 * {@code readObject} for it, and the record's components are checked like any other object of the message.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AllowedTypes {

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

    static boolean isAllowed(final Class<?> type) {
        final var component = component(type);
        if (component.isPrimitive())
            return true;

        if (type.isArray() && COLLECTION_ARRAY_COMPONENTS.contains(component))
            return true;

        return Message.class.isAssignableFrom(component)
                || component.isRecord()
                || Enum.class.isAssignableFrom(component)
                || ALLOWED_JDK_TYPES.contains(component)
                || ALLOWED_SUPERCLASSES.contains(component)
                || ALLOWED_JDK_INTERNALS.contains(component.getName());
    }

    /**
     * The element type of an array, however deeply nested, or the type itself.
     */
    static Class<?> component(final Class<?> type) {
        var component = type;
        while (component.isArray())
            component = component.getComponentType();

        return component;
    }
}
