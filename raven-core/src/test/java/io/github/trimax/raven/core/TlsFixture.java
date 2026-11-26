package io.github.trimax.raven.core;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Self-signed certificates for the TLS tests, generated with the {@code keytool} of the running JDK into a temporary
 * directory, so no key material lives in the repository. Created once per test run, on first use.
 */
final class TlsFixture {

    private static final String PASSWORD = "changeit";

    /** Server certificate for {@code localhost} and {@code 127.0.0.1}. */
    static final Identity SERVER = identity("server", "san=dns:localhost,ip:127.0.0.1");

    /** Another server certificate, which is valid for neither of them. */
    static final Identity OTHER_HOST = identity("other-host", "san=dns:raven.invalid");

    static final Identity CLIENT = identity("client", "san=dns:client.invalid");

    /** A certificate that none of the others trusts. */
    static final Identity STRANGER = identity("stranger", "san=dns:localhost,ip:127.0.0.1");

    private TlsFixture() {
    }

    /**
     * A context that presents {@code own} and trusts the certificates of {@code trusted}.
     */
    static SSLContext context(final Identity own, final Identity... trusted) {
        return build(own, trusted);
    }

    /**
     * A context that only trusts, and presents nothing.
     */
    static SSLContext trustingOnly(final Identity... trusted) {
        return build(null, trusted);
    }

    private static SSLContext build(final Identity own, final Identity[] trusted) {
        try {
            final var trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, null);
            for (final var identity : trusted)
                trustStore.setCertificateEntry(identity.name(), identity.certificate());

            final var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);

            final var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(own == null ? emptyStore() : own.keyStore(), PASSWORD.toCharArray());

            final var context = SSLContext.getInstance("TLS");
            context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
            return context;
        } catch (final GeneralSecurityException | IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyStore emptyStore() throws GeneralSecurityException, IOException {
        final var store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        return store;
    }

    private static Identity identity(final String name, final String extension) {
        try {
            final var directory = Files.createTempDirectory("raven-tls-" + name);
            directory.toFile().deleteOnExit();
            final var file = directory.resolve(name + ".p12");
            file.toFile().deleteOnExit();

            final var process = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                    "-genkeypair", "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                    "-dname", "CN=" + name, "-ext", extension,
                    "-keystore", file.toString(), "-storetype", "PKCS12",
                    "-storepass", PASSWORD, "-keypass", PASSWORD)
                    .redirectErrorStream(true)
                    .start();
            final var output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0)
                throw new IllegalStateException("keytool failed: " + output);

            return new Identity(name, file);
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    record Identity(String name, Path file) {

        KeyStore keyStore() throws GeneralSecurityException, IOException {
            final var store = KeyStore.getInstance("PKCS12");
            try (final var input = new FileInputStream(file.toFile())) {
                store.load(input, PASSWORD.toCharArray());
            }
            return store;
        }

        java.security.cert.Certificate certificate() throws GeneralSecurityException, IOException {
            return keyStore().getCertificate(name);
        }
    }
}
