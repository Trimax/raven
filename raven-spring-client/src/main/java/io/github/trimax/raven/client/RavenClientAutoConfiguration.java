package io.github.trimax.raven.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.github.trimax.raven.core.RavenClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for the Raven client.
 * Creates a {@link RavenClient} bean configured from properties and connects automatically.
 *
 * <p>Required properties: {@code raven.client.host}, {@code raven.client.port}
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.github.trimax.raven.client")
@RequiredArgsConstructor
public final class RavenClientAutoConfiguration {

    @Value("${raven.client.host}")
    private final String host;

    @Value("${raven.client.port}")
    private final int port;

    private RavenClient client;

    @Bean
    public RavenClient ravenClient(final ClientMessageRouter router) {
        client = new RavenClient(host, port, router);
        client.connect();
        return client;
    }

    @PreDestroy
    void shutdown() {
        if (client != null) {
            client.disconnect();
        }
    }
}
