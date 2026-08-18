package io.github.trimax.raven.example;

import io.github.trimax.raven.client.RavenClientAutoConfiguration;
import io.github.trimax.raven.core.RavenClient;
import io.github.trimax.raven.example.model.EchoMessage;
import io.github.trimax.raven.server.RavenServerAutoConfiguration;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Slf4j
@SpringBootApplication
@Import({RavenServerAutoConfiguration.class, RavenClientAutoConfiguration.class})
@RequiredArgsConstructor
public class ExampleApplication implements CommandLineRunner {

    private final RavenClient client;

    static void main(final String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }

    @Override
    public void run(final String @NonNull ... args) throws Exception {
        Thread.sleep(500); // wait for connection to establish

        if (!client.isConnected())
            client.connect();

        log.info("[App] Sending echo message...");
        client.send(new EchoMessage("Hello, Raven!"));

        Thread.sleep(1000); // wait for echo response
        log.info("[App] Done.");
    }
}
