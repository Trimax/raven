package io.github.trimax.raven.core;

import lombok.experimental.UtilityClass;

/**
 * Opens the package-private test hooks of {@link RavenServer} to tests in other packages, such as the internal ones.
 */
@UtilityClass
public final class RavenServerTestAccess {

    /**
     * Returns {@link RavenServer#availableConnections()}.
     */
    public int availableConnections(final RavenServer server) {
        return server.availableConnections();
    }
}
