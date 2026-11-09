package io.github.trimax.raven.core.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.internal.codec.MessageInputFilter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The default codec: Java serialization, one self-contained object stream per message.
 *
 * <p>The peer is untrusted, so decoding applies a {@code MessageInputFilter}: only {@link Message} subclasses, records
 * (unless disabled), enums and a fixed set of safe JDK value types may be deserialized. Records are safe to allow:
 * the JVM rebuilds a record only through its canonical constructor and never runs a {@code readObject} for it, and
 * the record's components pass the same filter. Each message gets a fresh filter, so the filter's limits are the
 * limits of that message.
 */
@Slf4j
@AllArgsConstructor
public final class JavaSerializationCodec implements MessageCodec {

    private final boolean allowRecords;

    /**
     * The default codec, which allows records.
     */
    public JavaSerializationCodec() {
        this(true);
    }

    /**
     * A strict codec that also rejects records, e.g. to allow nothing but {@link Message} subclasses as payload types.
     */
    public static JavaSerializationCodec withoutRecords() {
        return new JavaSerializationCodec(false);
    }

    @Override
    public byte[] encode(final Message message) throws IOException {
        final var bytes = new ByteArrayOutputStream();
        try (final var output = new ObjectOutputStream(bytes)) {
            output.writeObject(message);
        }

        return bytes.toByteArray();
    }

    @Override
    public Message decode(final byte[] payload) throws IOException {
        try (final var input = new ObjectInputStream(new ByteArrayInputStream(payload))) {
            input.setObjectInputFilter(new MessageInputFilter(allowRecords));

            final var object = input.readObject();
            if (object instanceof Message message)
                return message;

            log.warn("Received an object that is not a Message: {}", object == null ? "null" : object.getClass().getName());
            return null;
        } catch (final ClassNotFoundException ex) {
            // Passed the filter, e.g. a Message subclass the peer has and we do not
            throw new InvalidClassException(ex.getMessage(), "class not found");
        }
    }
}
