package io.github.trimax.raven.core.internal.net;

import java.io.IOException;
import java.net.ServerSocket;
import javax.net.ssl.SSLServerSocket;

import io.github.trimax.raven.core.config.RavenServerConfiguration;
import lombok.experimental.UtilityClass;

/**
 * Creates the listening socket of a server: a plain one, or, if the configuration has an SSL context, one that
 * accepts TLS connections.
 *
 * <p>Internal: not part of the API.
 */
@UtilityClass
public class RavenServerSocketFactory {

    /**
     * Binds a server socket as configured.
     *
     * @throws IOException if the socket cannot be bound
     */
    public ServerSocket create(final RavenServerConfiguration configuration) throws IOException {
        final var sslContext = configuration.getSslContext();

        // Backlog 0 keeps the JDK default; a null bind address listens on all local addresses
        if (sslContext == null)
            return new ServerSocket(configuration.getPort(), 0, configuration.getBindAddress());

        final var socket = (SSLServerSocket) sslContext.getServerSocketFactory()
                .createServerSocket(configuration.getPort(), 0, configuration.getBindAddress());
        // The accepted sockets inherit it. The handshake itself runs later, on the session thread of each connection,
        // so a slow client holds up neither accept() nor the handshake timeout
        socket.setNeedClientAuth(configuration.isRequireClientCertificate());
        return socket;
    }
}
