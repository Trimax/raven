package io.github.trimax.raven.example.handler;

import io.github.trimax.raven.client.SubscribeConnect;
import io.github.trimax.raven.client.SubscribeMessage;
import io.github.trimax.raven.example.model.EchoMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class ClientEchoHandler {

    @SubscribeMessage(EchoMessage.class)
    public void onEcho(final EchoMessage message) {
        log.info("[Client] Received echo: '{}'", message.getText());
    }

    @SubscribeConnect
    public void onConnect() {
        log.info("[Client] Connected to server");
    }
}
