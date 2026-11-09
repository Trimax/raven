package io.github.trimax.raven.core.internal.session;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WireFormat}, on in-memory streams.
 */
class FramesTest {

    @Test
    void acceptsItsOwnHello() throws IOException {
        final var bytes = new ByteArrayOutputStream();
        WireFormat.writeHello(new DataOutputStream(bytes));

        assertEquals(8, bytes.size());
        assertDoesNotThrow(() -> WireFormat.readHello(input(bytes.toByteArray())));
    }

    @Test
    void rejectsAForeignHello() {
        // What a 1.4.x peer sends first: the Java serialization stream header
        final var objectStreamHeader = new byte[]{(byte) 0xAC, (byte) 0xED, 0x00, 0x05, 0x73, 0x72, 0x00, 0x01};

        assertThrows(ProtocolException.class, () -> WireFormat.readHello(input(objectStreamHeader)));
    }

    @Test
    void rejectsAnotherProtocolVersion() throws IOException {
        final var bytes = new ByteArrayOutputStream();
        final var output = new DataOutputStream(bytes);
        output.writeBytes("RAVN");
        output.writeInt(WireFormat.PROTOCOL_VERSION + 1);

        final var ex = assertThrows(ProtocolException.class, () -> WireFormat.readHello(input(bytes.toByteArray())));
        assertTrue(ex.getMessage().contains(String.valueOf(WireFormat.PROTOCOL_VERSION + 1)));
    }

    @Test
    void reportsATruncatedHello() {
        assertThrows(EOFException.class, () -> WireFormat.readHello(input(new byte[]{'R', 'A'})));
    }

    @Test
    void roundTripsFramesInOrder() throws IOException {
        final var bytes = new ByteArrayOutputStream();
        final var output = new DataOutputStream(bytes);
        WireFormat.writeFrame(output, new byte[]{1, 2, 3});
        WireFormat.writeFrame(output, new byte[0]);
        WireFormat.writeFrame(output, new byte[]{4});

        final var input = input(bytes.toByteArray());
        assertArrayEquals(new byte[]{1, 2, 3}, WireFormat.readFrame(input, 3));
        assertArrayEquals(new byte[0], WireFormat.readFrame(input, 3));
        assertArrayEquals(new byte[]{4}, WireFormat.readFrame(input, 3));
        assertThrows(EOFException.class, () -> WireFormat.readFrame(input, 3));
    }

    @Test
    void rejectsAnOversizeFrame() throws IOException {
        assertThrows(ProtocolException.class, () -> WireFormat.readFrame(input(frame(5, new byte[5])), 4));
    }

    @Test
    void rejectsANegativeLength() throws IOException {
        assertThrows(ProtocolException.class, () -> WireFormat.readFrame(input(frame(-1, new byte[0])), 4));
    }

    @Test
    void reportsATruncatedFrame() throws IOException {
        assertThrows(EOFException.class, () -> WireFormat.readFrame(input(frame(10, new byte[3])), 16));
    }

    @Test
    void doesNotAllocateTheDeclaredLengthUpFront() throws IOException {
        // An array of Integer.MAX_VALUE bytes cannot be allocated at all: reading it up front would throw
        // OutOfMemoryError, while reading what arrives fails cleanly at the end of the three bytes
        final var input = input(frame(Integer.MAX_VALUE, new byte[3]));

        assertThrows(EOFException.class, () -> WireFormat.readFrame(input, Integer.MAX_VALUE));
    }

    private static byte[] frame(final int declaredLength, final byte[] payload) throws IOException {
        final var bytes = new ByteArrayOutputStream();
        final var output = new DataOutputStream(bytes);
        output.writeInt(declaredLength);
        output.write(payload);
        return bytes.toByteArray();
    }

    private static DataInputStream input(final byte[] bytes) {
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }
}
