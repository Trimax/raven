package io.github.trimax.raven.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DataSize}.
 */
class DataSizeTest {

    @Test
    void factoriesUseBinaryUnits() {
        assertEquals(512, DataSize.ofBytes(512).toBytes());
        assertEquals(64 * 1024, DataSize.ofKilobytes(64).toBytes());
        assertEquals(16L * 1024 * 1024, DataSize.ofMegabytes(16).toBytes());
        assertEquals(1L << 30, DataSize.ofGigabytes(1).toBytes());
    }

    @Test
    void parsesNumbersWithAndWithoutUnits() {
        assertEquals(DataSize.ofBytes(512), DataSize.parse("512"));
        assertEquals(DataSize.ofBytes(512), DataSize.parse("512B"));
        assertEquals(DataSize.ofKilobytes(64), DataSize.parse("64KB"));
        assertEquals(DataSize.ofMegabytes(16), DataSize.parse("16mb"));
        assertEquals(DataSize.ofMegabytes(16), DataSize.parse(" 16 MB "));
        assertEquals(DataSize.ofGigabytes(1), DataSize.parse("1GB"));
        assertEquals(1L << 40, DataSize.parse("1TB").toBytes());
    }

    @Test
    void rejectsMalformedText() {
        assertThrows(IllegalArgumentException.class, () -> DataSize.parse("big"));
        assertThrows(IllegalArgumentException.class, () -> DataSize.parse("16 megabytes"));
        assertThrows(IllegalArgumentException.class, () -> DataSize.parse("-1KB"));
        assertThrows(IllegalArgumentException.class, () -> DataSize.parse("1.5MB"));
    }

    @Test
    void rejectsOverflowAndNegativeSizes() {
        assertThrows(IllegalArgumentException.class, () -> DataSize.parse("99999999999TB"));
        assertThrows(ArithmeticException.class, () -> DataSize.ofGigabytes(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> DataSize.ofBytes(-1));
    }
}
