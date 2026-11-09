package io.github.trimax.raven.core.internal.codec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InvalidClassException;
import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.github.trimax.raven.core.Message;

/**
 * Unit tests for {@link MessageClassMatcher}: class names as the stream reports them, arrays included.
 */
class MessageClassMatcherTest {

    /** Set by the static initializer of {@link Gadget}; outside it, so reading it does not initialize Gadget. */
    static final AtomicBoolean GADGET_INITIALIZED = new AtomicBoolean();

    /** Referenced by name only: any class literal use could be mistaken for the matcher initializing it. */
    private static final String GADGET = "io.github.trimax.raven.core.internal.codec.MessageClassMatcherTest$Gadget";

    private final Rejection rejection = new Rejection();
    private final MessageClassMatcher matcher = new MessageClassMatcher(new MessageTypePolicy(Set.of()), rejection);

    static final class PingMessage extends Message {
    }

    record Point(int x, int y) implements Serializable {
    }

    static final class Payload implements Serializable {
    }

    /** A class whose static initializer has a side effect, as many deserialization gadgets do. */
    @SuppressWarnings("unused")
    static final class Gadget implements Serializable {
        static {
            GADGET_INITIALIZED.set(true);
        }
    }

    @Test
    void acceptsTheBuiltInTypes() {
        assertTrue(matcher.matches(PingMessage.class.getName()));
        assertTrue(matcher.matches(Point.class.getName()));
        assertTrue(matcher.matches("java.lang.String"));
        assertTrue(matcher.matches("java.util.ImmutableCollections$ListN"));
    }

    @Test
    void acceptsArraysByTheirStreamNames() {
        assertTrue(matcher.matches("[I"));
        assertTrue(matcher.matches("[[Ljava.lang.String;"));
        assertTrue(matcher.matches("[L" + Point.class.getName() + ";"));
        assertTrue(matcher.matches("[Ljava.lang.Object;"));
    }

    @Test
    void rejectsOtherClassesAndTheirArrays() {
        assertFalse(matcher.matches(Payload.class.getName()));
        assertFalse(matcher.matches("[L" + Payload.class.getName() + ";"));
        assertFalse(matcher.matches("java.util.Date"));
    }

    @Test
    void rejectsUnknownClasses() {
        assertFalse(matcher.matches("com.example.NoSuchMessage"));

        final var explained = assertInstanceOf(RejectedMessageException.class, rejection.explain(new InvalidClassException("rejected")));
        assertTrue(explained.reason().contains("no such class"), explained.reason());
    }

    @Test
    void doesNotInitializeARejectedClass() {
        assertFalse(matcher.matches(GADGET));

        assertFalse(GADGET_INITIALIZED.get(), "the static initializer of a rejected class must not run");
    }

    @Test
    void pointsToTheAllowedTypesForARejectedJdkClass() {
        matcher.matches("java.util.Arrays$ArrayList");

        final var explained = assertInstanceOf(RejectedMessageException.class, rejection.explain(new InvalidClassException("rejected")));
        assertTrue(explained.reason().contains("not one of the allowed JDK types"), explained.reason());
    }

    @Test
    void acceptsClassesOfTrustedPackages() {
        final var trusting = new MessageClassMatcher(new MessageTypePolicy(Set.of("io.github.trimax.raven.core")), rejection);

        assertTrue(trusting.matches(Payload.class.getName()));
        assertTrue(trusting.matches("[L" + Payload.class.getName() + ";"));
        assertFalse(trusting.matches("java.util.Date"));
    }

    @Test
    void hintsHowToTrustARejectedClass() {
        matcher.matches(Payload.class.getName());

        final var explained = assertInstanceOf(RejectedMessageException.class, rejection.explain(new InvalidClassException("rejected")));
        assertTrue(explained.subject().contains(Payload.class.getName()), explained.subject());
        assertTrue(explained.reason().contains("trusted-packages=io.github.trimax.raven.core.internal.codec"), explained.reason());
        assertTrue(explained.reason().contains("new JavaSerializationCodec(Set.of(\"io.github.trimax.raven.core.internal.codec\"))"),
                explained.reason());
    }
}
