package io.github.trimax.raven.example.handler;

import io.github.trimax.raven.core.Client;
import io.github.trimax.raven.core.RavenServer;
import io.github.trimax.raven.example.model.EchoMessage;
import io.github.trimax.raven.server.SubscribeConnect;
import io.github.trimax.raven.server.SubscribeDisconnect;
import io.github.trimax.raven.server.SubscribeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public final class ServerEchoHandler {

    private final RavenServer server;

    @SubscribeMessage(EchoMessage.class)
    public void onEcho(final Client sender, final EchoMessage message) {
        log.info("[Server] Received: '{}' from {}", message.getText(), sender.getId());
        server.send(new EchoMessage("Echo: " + message.getText()), sender.getId());
    }

    @SubscribeConnect
    public void onConnect(final Client client) {
        log.info("[Server] Client connected: {}", client.getId());
    }

    @SubscribeDisconnect
    public void onDisconnect(final Client client) {
        log.info("[Server] Client disconnected: {}", client.getId());
    }
}
