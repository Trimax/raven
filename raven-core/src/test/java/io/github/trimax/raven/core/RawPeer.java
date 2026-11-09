package io.github.trimax.raven.core;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import lombok.experimental.UtilityClass;

/**
 * Speaks the wire format by hand, for tests that play a peer which bypasses {@link RavenClient}: one that skips
 * send-side validation, stalls in the handshake or sends objects that are not messages.
 */
@UtilityClass
public final class RawPeer {

    public void writeHello(final OutputStream output) throws IOException {
        WireFormat.writeHello(new DataOutputStream(output));
    }

    public void readHello(final InputStream input) throws IOException {
        WireFormat.readHello(new DataInputStream(input));
    }

    /**
     * Writes any object as one Java-serialized frame, as the default codec would write a message.
     */
    public void writeObject(final OutputStream output, final Object object) throws IOException {
        final var data = new DataOutputStream(output);
        WireFormat.writeFrame(data, serialize(object));
        data.flush();
    }

    public byte[] serialize(final Object object) throws IOException {
        final var bytes = new ByteArrayOutputStream();
        try (final var stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(object);
        }
        return bytes.toByteArray();
    }
}
