# Raven

Raven is a lightweight TCP networking framework for Java. It provides a pure Java transport layer and optional Spring integration with annotation-based message routing.

Named after messenger ravens — they carry your messages reliably.
Dedicated to my friend — the best 3D artist I know: https://www.deviantart.com/rav3nway

## Features

- Pure Java TCP transport (no Spring required)
- Virtual threads for scalable I/O
- Java Serialization over ObjectStream
- Annotation-based message dispatch for Spring applications
- Separate server and client routers with signature validation at startup
- Automatic reconnection with configurable retry strategies
- Zero-configuration Spring auto-setup via properties

## Modules

| Module                | Description                                                                                         |
|-----------------------|-----------------------------------------------------------------------------------------------------|
| `raven-core`          | Pure Java transport: `RavenServer`, `RavenClient`, `Message`, `Client`                              |
| `raven-spring-core`   | Spring base: `AbstractMessageRouter`, `HandlerMethod`                                               |
| `raven-spring-server` | Server-side router + annotations (`@SubscribeMessage`, `@SubscribeConnect`, `@SubscribeDisconnect`) |
| `raven-spring-client` | Client-side router + annotations (`@SubscribeMessage`, `@SubscribeConnect`, `@SubscribeDisconnect`) |

## Quick Start

### 1. Define a message

```java
public final class PingMessage extends Message {
}

public final class PongMessage extends Message {
    private final long serverTime;
}
```

### 2. Server (Spring Boot)

Add dependency:
```xml
<dependency>
    <groupId>io.github.trimax</groupId>
    <artifactId>raven-spring-server</artifactId>
    <version>1.0.0</version>
</dependency>
```

Import the autoconfiguration:
```java
@SpringBootApplication
@Import(RavenServerAutoConfiguration.class)
public class ServerApp { }
```

Configure:
```properties
raven.server.port=9090
```

Write a handler:
```java
@Component
@RequiredArgsConstructor
public final class PingHandler {
    private final RavenServer server;

    @SubscribeMessage(PingMessage.class)
    public void onPing(Client sender, PingMessage message) {
        server.send(new PongMessage(System.currentTimeMillis()), sender.getId());
    }

    @SubscribeConnect
    public void onConnect(Client client) {
        log.info("Client connected: {}", client.getId());
    }
}
```

### 3. Client (Spring Boot)

Add dependency:
```xml
<dependency>
    <groupId>io.github.trimax</groupId>
    <artifactId>raven-spring-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

Import the autoconfiguration:
```java
@SpringBootApplication
@Import(RavenClientAutoConfiguration.class)
public class ClientApp { }
```

Configure:
```properties
raven.client.host=localhost
raven.client.port=9090
```

Write a handler:
```java
@Component
public final class PongHandler {

    @SubscribeMessage(PongMessage.class)
    public void onPong(PongMessage message) {
        log.info("Server time: {}", message.getServerTime());
    }

    @SubscribeConnect
    public void onConnect() {
        log.info("Connected to server");
    }
}
```

### 4. Without Spring (pure Java)

```java
var server = new RavenServer(9090, new ServerHandler() {
    public void onConnect(Client client) { }
    public void onDisconnect(Client client) { }
    public void onMessage(Client sender, Message message) {
        server.send(new PongMessage(), sender.getId());
    }
});
server.start();

var client = new RavenClient("localhost", 9090, new ClientHandler() {
    public void onConnect() { client.send(new PingMessage()); }
    public void onDisconnect() { }
    public void onMessage(Message message) { }
});
client.connect();
```

## Send API

```java
// Broadcast to all connected clients
server.send(message);

// Send a message to specific client(s)
server.send(message, clientId);
server.send(message, clientA, clientB);
```

## Handler Signatures

### Server-side (`raven-spring-server`)

```java
@SubscribeMessage(MyMessage.class)
void method(Client sender, MyMessage message)

@SubscribeConnect
void method(Client client)

@SubscribeDisconnect
void method(Client client)
```

### Client-side (`raven-spring-client`)

```java
@SubscribeMessage(MyMessage.class)
void method(MyMessage message)

@SubscribeConnect
void method()

@SubscribeDisconnect
void method()
```

## Requirements

- Java 21+ (virtual threads)
- Spring Framework 6+ (for spring modules)

## License

Apache License 2.0
