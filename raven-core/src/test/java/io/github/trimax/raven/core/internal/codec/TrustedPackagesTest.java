package io.github.trimax.raven.core.internal.codec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TrustedPackages}: a package trusts itself and its subpackages, nothing else.
 */
class TrustedPackagesTest {

    private final TrustedPackages trusted = new TrustedPackages(Set.of("com.example"));

    @Test
    void trustsThePackageAndItsSubpackages() {
        assertTrue(trusted.trusts("com.example"));
        assertTrue(trusted.trusts("com.example.dto"));
        assertTrue(trusted.trusts("com.example.dto.v2"));
    }

    @Test
    void doesNotTrustNeighbouringOrParentPackages() {
        assertFalse(trusted.trusts("com.examplex"));
        assertFalse(trusted.trusts("com.exampl"));
        assertFalse(trusted.trusts("com"));
        assertFalse(trusted.trusts(""));
    }

    @Test
    void trustsNothingByDefault() {
        assertFalse(new TrustedPackages(Set.of()).trusts("com.example"));
    }

    @Test
    void starTrustsEveryPackage() {
        final var everything = new TrustedPackages(Set.of(TrustedPackages.EVERYTHING));

        assertTrue(everything.trusts("java.lang.reflect"));
        assertTrue(everything.trusts(""));
    }

    @Test
    void rejectsEntriesThatAreNotPackageNames() {
        assertThrows(IllegalArgumentException.class, () -> new TrustedPackages(Set.of("com.example.*")));
        assertThrows(IllegalArgumentException.class, () -> new TrustedPackages(Set.of("com.example.")));
        assertThrows(IllegalArgumentException.class, () -> new TrustedPackages(Set.of("")));
        assertThrows(IllegalArgumentException.class, () -> new TrustedPackages(Set.of("com/example")));
    }
}
