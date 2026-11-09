package io.github.trimax.raven.core.internal.codec;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Packages whose classes a message may contain on top of the {@link AllowedTypes}: a package trusts its own classes
 * and those of its subpackages, and {@value #EVERYTHING} trusts every class.
 *
 * @param packages the package names, or {@value #EVERYTHING}
 */
record TrustedPackages(Set<String> packages) {

    /** Trusts every class on the class path. */
    static final String EVERYTHING = "*";

    private static final Pattern PACKAGE_NAME = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*"
            + "(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*");

    /**
     * @throws IllegalArgumentException if an entry is neither a package name nor {@value #EVERYTHING}, e.g.
     *                                  {@code com.example.*}, which would silently trust nothing
     */
    TrustedPackages {
        packages = Set.copyOf(packages);
        for (final var name : packages)
            if (!name.equals(EVERYTHING) && !PACKAGE_NAME.matcher(name).matches())
                throw new IllegalArgumentException("Not a package name: '" + name
                        + "'; use 'com.example' to trust a package and its subpackages, or '*' to trust every class");
    }

    /**
     * Whether classes of the package are trusted.
     *
     * @param packageName the package of the class, empty for the unnamed package
     */
    boolean trusts(final String packageName) {
        if (packages.contains(EVERYTHING))
            return true;

        // A subpackage must continue after a dot: "com.example" trusts "com.example.dto", not "com.examplex"
        for (final var trusted : packages)
            if (packageName.startsWith(trusted)
                    && (packageName.length() == trusted.length() || packageName.charAt(trusted.length()) == '.'))
                return true;

        return false;
    }
}
