package io.github.trimax.raven.core.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.util.Set;

import org.apache.commons.io.serialization.ValidatingObjectInputStream;

import io.github.trimax.raven.core.Message;
import io.github.trimax.raven.core.internal.codec.MessageTypePolicy;
import io.github.trimax.raven.core.internal.codec.Rejection;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * The default codec: Java serialization, one self-contained object stream per message.
 *
 * <p>The peer is untrusted, so decoding accepts only {@link Message} subclasses, records, enums, primitives, a fixed
 * set of safe JDK value types and arrays of these; any other class is rejected before it is initialized, its
 * constructor or {@code readObject} runs. Records are always allowed: the JVM rebuilds a record only through its
 * canonical constructor and never runs a {@code readObject} for it, and the record's components are checked as well.
 * Each message is also limited in nesting depth, object references and array elements.
 *
 * <p>Plain classes, e.g. DTOs that are neither messages nor records, are allowed only from trusted packages, none by
 * default. A package trusts its classes and those of its subpackages: {@code com.example} trusts
 * {@code com.example.Unit} and {@code com.example.dto.Order}, but not {@code com.examplex.Unit}. Only trust packages
 * whose classes are safe to deserialize from a peer: their {@code readObject} methods run on the peer's data.
 * {@code "*"} trusts every class on the class path, which reopens Java deserialization gadget attacks; use it only
 * when every peer is trusted, e.g. on a private network.
 */
@Slf4j
public final class JavaSerializationCodec implements MessageCodec {

    /** The packages whose classes are allowed on top of the built-in types. */
    @Getter
    private final Set<String> trustedPackages;

    private final MessageTypePolicy policy;

    /**
     * The default codec: built-in types only, no trusted packages.
     */
    public JavaSerializationCodec() {
        this(Set.of());
    }

    /**
     * A codec that also allows the classes of the given packages and their subpackages.
     *
     * @param trustedPackages package names such as {@code com.example}, or {@code "*"} to trust every class (only for
     *                        trusted peers, see the class documentation)
     * @throws IllegalArgumentException if an entry is not a package name, e.g. {@code com.example.*}
     */
    public JavaSerializationCodec(@NonNull final Set<String> trustedPackages) {
        this.trustedPackages = Set.copyOf(trustedPackages);
        this.policy = new MessageTypePolicy(this.trustedPackages);
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
        final var rejection = new Rejection();
        // Declared as ValidatingObjectInputStream, not var or ObjectInputStream: static analysis (CodeQL) recognizes
        // a stream that checks classes before resolving them by this type
        try (final ValidatingObjectInputStream input = ValidatingObjectInputStream.builder()
                .setByteArray(payload)
                .accept(policy.matcher(rejection))
                .get()) {
            input.setObjectInputFilter(policy.filter(rejection));

            final var object = input.readObject();
            if (object instanceof Message message)
                return message;

            log.warn("Received an object that is not a Message: {}", object == null ? "null" : object.getClass().getName());
            return null;
        } catch (final InvalidClassException ex) {
            // Tells the log which class or limit it was; the stream itself only says that something was rejected
            throw rejection.explain(ex);
        } catch (final ClassNotFoundException ex) {
            // Passed the checks, yet the stream did not find it: it resolves with another class loader
            throw new InvalidClassException(ex.getMessage(), "class not found");
        }
    }
}
