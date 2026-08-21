# Code Style Guide

This document defines the coding conventions used across the Raven project.

---

## 1. General Principles

- **Immutability by default**: all local variables, parameters, and catch variables are `final`.
- **Explicit over implicit**: no wildcard imports, no `@Autowired` field injection, constructor injection only.
- **One class, one responsibility**: each class has a single clear purpose.
- **Convention over configuration**: consistent naming patterns make code predictable.

---

## 2. Class Design

- All concrete classes are `final` unless designed for extension.
- Abstract classes are used only when subclassing is intentional (e.g., `AbstractMessageRouter`).
- Spring `@Configuration` classes use `proxyBeanMethods = false` to allow `final`.
- Records are preferred for value objects and data carriers (e.g., `HandlerMethod`).
- Utility classes use `@NoArgsConstructor(access = AccessLevel.PRIVATE)`.

---

## 3. Lombok Usage

- **Lombok-first**: if Lombok can generate it, use Lombok. Do not write boilerplate that an annotation covers (constructors, getters, builders, equals/hashCode, toString, loggers).
- `@RequiredArgsConstructor` for dependency injection (never `@Autowired`).
- `@Slf4j` for logging on every class that logs.
- `@Getter` for exposing state; `@Setter` avoided.
- `@NonNull` on constructor parameters that must not be null.
- `@NoArgsConstructor` / `@AllArgsConstructor` on message/model classes for serialization.

---

## 4. Formatting

- **Indentation**: 4 spaces.
- **Single-statement blocks**: no braces for `if`, `for`, `while` with a single-line body.
  ```java
  if (connection == null)
      return;

  for (final var client : clients)
      client.disconnect();
  ```
- **Multi-statement blocks**: always use braces.
- **Blank lines**: one between methods, one between logical sections within a method.
- **Max line length**: prefer readability; no hard wrap at 80/120.

---

## 5. Naming Conventions

- Packages: all lowercase, no underscores (`io.github.trimax.raven.core`).
- Classes: PascalCase, suffix indicates role (`RavenServer`, `ServerMessageRouter`, `ClientHandler`).
- Methods: camelCase (`onConnect`, `invokeMessageHandlers`).
- Constants: `UPPER_SNAKE_CASE` (`INITIAL_RETRY_DELAY_MS`).
- Annotations: PascalCase matching their purpose (`@SubscribeMessage`, `@SubscribeConnect`).
- **Unused parameters**: use `_` where the language allows (lambdas, multi-catch); otherwise name them `ignored` (e.g., `final SomeEvent ignored`).

---

## 6. Import Organization

- No wildcard imports.
- No unused imports.
- No redundant same-package imports.
- Order: java.*, external libraries, project imports (IDE auto-sort).

---

## 7. Annotations

- `@Override` always present when overriding.
- Annotations on separate lines above the declaration (not inline).
- Multiple class annotations stacked: `@Slf4j`, `@Component`, `@RequiredArgsConstructor`.

---

## 8. Error Handling

- Exceptions in handlers are logged and swallowed (fault isolation).
- Transport-level errors set state (`connected = false`) and return null/early.
- Validation errors at startup throw `IllegalStateException` with descriptive messages.
- `@PreDestroy` methods handle cleanup gracefully (null checks before close/stop).

---

## 9. Threading

- Virtual threads (`Thread.ofVirtual()`) for all I/O-bound operations.
- `synchronized` on methods/blocks for rare operations (connect/disconnect).
- `AtomicReference` for state management over `volatile boolean` where CAS is needed.
- `ConcurrentHashMap` for shared collections.

---

## 10. Documentation

- Javadoc on all public classes and methods.
- `@param`, `@return` for non-obvious parameters.
- Implementation notes as inline comments only when logic is non-trivial.
- No commented-out code.

---

## 11. Testing

- Unit tests for individual class behavior.
- Integration tests for multi-component flows (server + client together).
- `@ExtendWith(SpringExtension.class)` + `@ContextConfiguration` for Spring tests (not `@SpringBootTest` to avoid auto-scanning conflicts).
- Awaitility for async assertions with explicit timeouts.
- Test class naming: `{Class}Test` for unit, `{Class}IntegrationTest` for integration.

---

## 12. Dependency Injection (Spring)

- `@Configuration(proxyBeanMethods = false)` for all config classes.
- `@ComponentScan(basePackages = ...)` on auto-configurations to self-contain scanning.
- `ObjectProvider<T>` for optional dependencies.
- `@Value("${property}")` with `@RequiredArgsConstructor` (via `lombok.copyableAnnotations`).
- No circular dependencies — design around them with events or callbacks.
