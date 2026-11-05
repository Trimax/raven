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
import java.util.Set;
import java.util.UUID;

import io.github.trimax.raven.core.Message;

/**
 * Deserialization filter applied to every object read from the network.
 *
 * <p>Only {@link Message} subclasses, a small set of safe JDK value types and enums are accepted.
 * Any other class (including classes that are typically used as deserialization gadgets) is rejected
 * before its constructor or {@code readObject} logic runs. Structural limits (depth, number of
 * references and array length) protect against resource-exhaustion payloads.
 */
public final class MessageInputFilter implements ObjectInputFilter {

    public static final MessageInputFilter INSTANCE = new MessageInputFilter();

    static final long MAX_DEPTH = 64;
    static final long MAX_REFERENCES = 100_000;
    static final long MAX_ARRAY_LENGTH = 1 << 20;

    private static final Set<Class<?>> ALLOWED_JDK_TYPES = Set.of(
            String.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, Character.class, UUID.class, BigInteger.class, BigDecimal.class,
            Instant.class, Duration.class, LocalDate.class, LocalTime.class, LocalDateTime.class,
            ArrayList.class, LinkedList.class, HashMap.class, LinkedHashMap.class,
            HashSet.class, LinkedHashSet.class);

    private MessageInputFilter() {
    }

    @Override
    public Status checkInput(final FilterInfo info) {
        if (info.depth() > MAX_DEPTH || info.references() > MAX_REFERENCES || info.arrayLength() > MAX_ARRAY_LENGTH)
            return Status.REJECTED;

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

        return Message.class.isAssignableFrom(component)
                || Enum.class.isAssignableFrom(component)
                || ALLOWED_JDK_TYPES.contains(component);
    }
}
