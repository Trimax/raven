package io.github.trimax.raven.server;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.datetime.standard.DurationFormatterUtils;

import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.core.codec.MessageCodec;
import io.github.trimax.raven.core.config.RavenServerConfiguration;
import io.github.trimax.raven.core.interceptor.ServerMessageInterceptor;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Autoconfiguration for the Raven server.
 * Creates a {@link RavenServer} bean configured from properties.
 * Server is started after all beans are initialized to ensure handlers are registered.
 *
 * <p>Required property: {@code raven.server.port}.
 * Optional: {@code raven.server.bind-address}, a host name or IP address to listen on (defaults to all local addresses),
 * {@code raven.server.send-queue-capacity}, {@code raven.server.send-timeout}, e.g. {@code 5s} or {@code PT5S},
 * {@code raven.server.handshake-timeout}, how long a new connection may take to deliver its hello,
 * {@code raven.server.max-connections}, {@code raven.server.max-message-size} in bytes, and
 * {@code raven.server.idle-timeout}, after which a client that sent nothing is disconnected; there is no heartbeat,
 * so this also disconnects healthy but silent clients (all but the bind address default to
 * {@link RavenServerConfiguration}'s values; the idle timeout is off by default). A {@link MessageCodec} bean, if
 * present, replaces the default Java serialization.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.github.trimax.raven.server")
@RequiredArgsConstructor
public final class RavenServerAutoConfiguration implements SmartInitializingSingleton {

    @Value("${raven.server.port}")
    private final int port;

    @Value("${raven.server.bind-address:#{null}}")
    private final String bindAddress;

    @Value("${raven.server.send-queue-capacity:#{null}}")
    private final Integer sendQueueCapacity;

    /**
     * Parsed here rather than injected as a {@link java.time.Duration}: plain Spring, unlike Spring Boot,
     * has no String-to-Duration conversion for {@code @Value}.
     */
    @Value("${raven.server.send-timeout:#{null}}")
    private final String sendTimeout;

    @Value("${raven.server.handshake-timeout:#{null}}")
    private final String handshakeTimeout;

    @Value("${raven.server.max-connections:#{null}}")
    private final Integer maxConnections;

    @Value("${raven.server.idle-timeout:#{null}}")
    private final String idleTimeout;

    @Value("${raven.server.max-message-size:#{null}}")
    private final Integer maxMessageSize;

    private RavenServer server;

    @Bean
    public RavenServer ravenServer(final ServerMessageRouter router,
                                   final ObjectProvider<ServerMessageInterceptor> interceptors,
                                   final ObjectProvider<MessageCodec> codec) {
        final var builder = RavenServerConfiguration.builder()
                .port(port)
                .bindAddress(resolve(bindAddress))
                .handler(router)
                .interceptors(interceptors.orderedStream().toList());
        // Unset keeps the core default instead of duplicating it here
        if (sendQueueCapacity != null)
            builder.sendQueueCapacity(sendQueueCapacity);
        if (sendTimeout != null)
            builder.sendTimeout(DurationFormatterUtils.detectAndParse(sendTimeout));
        if (handshakeTimeout != null)
            builder.handshakeTimeout(DurationFormatterUtils.detectAndParse(handshakeTimeout));
        if (maxConnections != null)
            builder.maxConnections(maxConnections);
        if (idleTimeout != null)
            builder.idleTimeout(DurationFormatterUtils.detectAndParse(idleTimeout));
        if (maxMessageSize != null)
            builder.maxMessageSize(maxMessageSize);
        // A MessageCodec bean replaces the default Java serialization
        final var customCodec = codec.getIfAvailable();
        if (customCodec != null)
            builder.codec(customCodec);

        server = new RavenServer(builder.build());
        return server;
    }

    private static InetAddress resolve(final String address) {
        if (address == null)
            return null;

        try {
            return InetAddress.getByName(address);
        } catch (final UnknownHostException ex) {
            throw new IllegalArgumentException("Invalid raven.server.bind-address: " + address, ex);
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        server.start();
    }

    @PreDestroy
    void shutdown() {
        if (server != null)
            server.stop();
    }
}
