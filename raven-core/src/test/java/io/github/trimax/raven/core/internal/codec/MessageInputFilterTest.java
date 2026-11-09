package io.github.trimax.raven.core.internal.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ObjectInputFilter.FilterInfo;
import java.io.ObjectInputFilter.Status;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Message;

/**
 * Unit tests for {@link MessageInputFilter}: which classes may be deserialized and the structural limits.
 */
class MessageInputFilterTest {

    private final MessageInputFilter filter = new MessageInputFilter();

    static final class PingMessage extends Message {
    }

    static final class Payload implements Serializable {
    }

    @Test
    void allowsMessageSubclasses() {
        assertEquals(Status.ALLOWED, check(PingMessage.class));
    }

    @Test
    void allowsSafeJdkValueTypes() {
        assertEquals(Status.ALLOWED, check(String.class));
        assertEquals(Status.ALLOWED, check(UUID.class));
        assertEquals(Status.ALLOWED, check(Instant.class));
        assertEquals(Status.ALLOWED, check(ArrayList.class));
    }

    @Test
    void allowsEnums() {
        assertEquals(Status.ALLOWED, check(TimeUnit.class));
    }

    @Test
    void allowsArraysOfAllowedTypes() {
        assertEquals(Status.ALLOWED, check(byte[].class));
        assertEquals(Status.ALLOWED, check(String[][].class));
        assertEquals(Status.ALLOWED, check(PingMessage[].class));
    }

    @Test
    void rejectsOtherSerializableClasses() {
        assertEquals(Status.REJECTED, check(Payload.class));
        assertEquals(Status.REJECTED, check(Payload[].class));
    }

    @Test
    void leavesStructuralChecksWithoutClassUndecided() {
        assertEquals(Status.UNDECIDED, filter.checkInput(info(null, 1, 1, -1)));
    }

    @Test
    void rejectsTooDeepGraphs() {
        assertEquals(Status.REJECTED, filter.checkInput(
                info(String.class, MessageInputFilter.MAX_DEPTH + 1, 1, -1)));
    }

    @Test
    void rejectsTooManyReferencesInOneMessage() {
        assertEquals(Status.ALLOWED, filter.checkInput(info(String.class, 1, 1, -1)));
        assertEquals(Status.REJECTED, filter.checkInput(info(String.class, 1, 2 + MessageInputFilter.MAX_REFERENCES, -1)));
    }

    @Test
    void allowsTheInternalArraysOfAllowedCollections() {
        assertEquals(Status.ALLOWED, check(Object[].class));
        assertEquals(Status.ALLOWED, check(Map.Entry[].class));
    }

    @Test
    void allowsTheSuperclassOfTheBoxedNumbers() {
        assertEquals(Status.ALLOWED, check(Number.class));
    }

    @Test
    void stillRejectsArraysOfOtherClasses() {
        assertEquals(Status.REJECTED, check(Thread[].class));
    }

    @Test
    void sumsArrayElementsAcrossTheArraysOfOneMessage() {
        // Nested arrays are allocated level by level before their data arrives, so the budget is shared
        final var half = MessageInputFilter.MAX_ARRAY_ELEMENTS / 2;
        assertEquals(Status.ALLOWED, filter.checkInput(info(long[][].class, 1, 1, half)));
        assertEquals(Status.ALLOWED, filter.checkInput(info(long[].class, 2, 2, half)));
        assertEquals(Status.REJECTED, filter.checkInput(info(long[].class, 2, 3, 1)));
    }

    @Test
    void rejectsTooLongArrays() {
        assertEquals(Status.REJECTED, filter.checkInput(
                info(byte[].class, 1, 1, MessageInputFilter.MAX_ARRAY_ELEMENTS + 1)));
    }

    @Test
    void allowsGraphsAtTheLimits() {
        assertEquals(Status.ALLOWED, filter.checkInput(info(byte[].class,
                MessageInputFilter.MAX_DEPTH, MessageInputFilter.MAX_REFERENCES, MessageInputFilter.MAX_ARRAY_ELEMENTS)));
    }

    private Status check(final Class<?> type) {
        return filter.checkInput(info(type, 1, 1, -1));
    }

    private static FilterInfo info(final Class<?> type, final long depth, final long references, final long arrayLength) {
        return new FilterInfo() {
            @Override
            public Class<?> serialClass() {
                return type;
            }

            @Override
            public long arrayLength() {
                return arrayLength;
            }

            @Override
            public long depth() {
                return depth;
            }

            @Override
            public long references() {
                return references;
            }

            @Override
            public long streamBytes() {
                return 0;
            }
        };
    }
}
