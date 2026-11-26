package io.github.trimax.raven.core.internal.net;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import javax.net.ssl.SSLSocket;

import io.github.trimax.raven.core.config.RavenClientConfiguration;
import lombok.experimental.UtilityClass;

/**
 * Creates the socket a client dials with: a plain one, or, if the configuration has an SSL context, a TLS one.
 * The socket is not connected; the TLS handshake starts once it is.
 *
 * <p>Internal: not part of the API.
 */
@UtilityClass
public class RavenClientSocketFactory {

    /**
     * @throws UncheckedIOException if the JDK cannot create the unconnected TLS socket, which it does not do in practice
     */
    public Socket create(final RavenClientConfiguration configuration) {
        final var sslContext = configuration.getSslContext();
        if (sslContext == null)
            return new Socket();

        final SSLSocket socket;
        try {
            socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }

        if (configuration.isVerifyHostname()) {
            final var parameters = socket.getSSLParameters();
            // Without it, the JDK checks that the certificate is trusted but not that it belongs to this server
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
        }

        return socket;
    }
}
