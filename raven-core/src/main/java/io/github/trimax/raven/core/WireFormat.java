package io.github.trimax.raven.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Arrays;

import lombok.experimental.UtilityClass;

/**
 * The wire format of a session. Each side first sends a hello, the magic bytes {@code RAVN} and the protocol
 * version; then every message travels as one frame, its length as an {@code int} followed by that many bytes of
 * codec output.
 */
@UtilityClass
final class WireFormat {

    private final byte[] MAGIC = {'R', 'A', 'V', 'N'};

    /** Version 2 introduced framing; 1.4.x wrote one long object stream and cannot talk to this one. */
    final int PROTOCOL_VERSION = 2;

    void writeHello(final DataOutputStream output) throws IOException {
        output.write(MAGIC);
        output.writeInt(PROTOCOL_VERSION);
        output.flush();
    }

    /**
     * Reads the peer's hello.
     *
     * @throws ProtocolException if the peer is not a Raven peer or speaks another protocol version
     */
    void readHello(final DataInputStream input) throws IOException {
        final var magic = input.readNBytes(MAGIC.length);
        if (magic.length < MAGIC.length)
            throw new EOFException("Connection closed during the handshake");

        if (!Arrays.equals(magic, MAGIC))
            throw new ProtocolException("Peer is not a Raven peer");

        final var version = input.readInt();
        if (version != PROTOCOL_VERSION)
            throw new ProtocolException("Peer speaks protocol version " + version + ", expected " + PROTOCOL_VERSION);
    }

    /**
     * Writes one payload as a frame, without flushing.
     */
    void writeFrame(final DataOutputStream output, final byte[] payload) throws IOException {
        output.writeInt(payload.length);
        output.write(payload);
    }

    /**
     * Reads one frame and returns its payload.
     *
     * @param maxSize the largest payload accepted
     * @throws ProtocolException if the declared length is negative or larger than {@code maxSize}
     * @throws EOFException      if the stream ends before the frame does
     */
    byte[] readFrame(final DataInputStream input, final int maxSize) throws IOException {
        final var length = input.readInt();
        if (length < 0 || length > maxSize)
            throw new ProtocolException("Frame of " + length + " bytes, the limit is " + maxSize);

        // Grows with the bytes that actually arrive, so a peer cannot make us allocate a large frame by declaring one
        final var payload = input.readNBytes(length);
        if (payload.length < length)
            throw new EOFException("Connection closed within a frame");

        return payload;
    }
}
