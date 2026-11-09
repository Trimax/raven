package io.github.trimax.raven.core.type;

import java.util.Locale;
import java.util.regex.Pattern;

import lombok.NonNull;

/**
 * An amount of data, like {@link java.time.Duration} is an amount of time. Units are binary: 1 KB = 1024 bytes.
 * Mirrors Spring's {@code DataSize}, which this core module cannot depend on.
 *
 * @param bytes the size in bytes; never negative
 */
public record DataSize(long bytes) {

    private static final Pattern FORMAT = Pattern.compile("(\\d+)\\s*([KMGT]?B)?", Pattern.CASE_INSENSITIVE);

    public DataSize {
        if (bytes < 0)
            throw new IllegalArgumentException("DataSize must not be negative, got " + bytes);
    }

    public static DataSize ofBytes(final long bytes) {
        return new DataSize(bytes);
    }

    public static DataSize ofKilobytes(final long kilobytes) {
        return new DataSize(Math.multiplyExact(kilobytes, 1L << 10));
    }

    public static DataSize ofMegabytes(final long megabytes) {
        return new DataSize(Math.multiplyExact(megabytes, 1L << 20));
    }

    public static DataSize ofGigabytes(final long gigabytes) {
        return new DataSize(Math.multiplyExact(gigabytes, 1L << 30));
    }

    /**
     * Parses a size such as {@code 512}, {@code 512B}, {@code 64KB}, {@code 16MB}, {@code 1GB} or {@code 1TB}
     * (case-insensitive; a bare number means bytes).
     *
     * @throws IllegalArgumentException if the text is not a size or the size overflows
     */
    public static DataSize parse(final String text) {
        final var matcher = FORMAT.matcher(text.strip());
        if (!matcher.matches())
            throw new IllegalArgumentException("Not a data size: '" + text + "', expected e.g. 512B, 64KB or 16MB");

        final var unit = matcher.group(2) == null ? "B" : matcher.group(2).toUpperCase(Locale.ROOT);
        final var shift = switch (unit) {
            case "KB" -> 10;
            case "MB" -> 20;
            case "GB" -> 30;
            case "TB" -> 40;
            default -> 0;
        };

        try {
            return new DataSize(Math.multiplyExact(Long.parseLong(matcher.group(1)), 1L << shift));
        } catch (final NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException("Data size too large: '" + text + "'", ex);
        }
    }

    public long toBytes() {
        return bytes;
    }

    @NonNull
    @Override
    public String toString() {
        return bytes + "B";
    }
}
