# AI Agent Instructions: Build a Spring Boot Circuit Breaker Interview Simulator

## Goal

Spin up a small Spring Boot codebase that simulates the kind of backend pair-programming exercise used to assess a candidate's practical coding ability.

The core exercise is a **single-threaded circuit breaker** around an unreliable external service.

The codebase should be deliberately small and interview-friendly. Do not build a production-grade distributed system. The purpose is to let the candidate practise:

- reading an existing codebase
- understanding a small domain model
- choosing data structures
- implementing state transitions
- handling time windows
- writing tests
- reasoning about edge cases
- explaining time/space complexity
- making a small change to existing code

## Stack

Use:

- Java 17 or later
- Spring Boot 3.x
- Gradle
- JUnit 5
- Spring Web only where useful for manually exercising the service

Keep dependencies to a minimum.

## Repository Shape

Create something roughly like:

```text
circuit-breaker-interview/
├── build.gradle
├── settings.gradle
├── README.md
└── src/
    ├── main/
    │   └── java/com/example/circuitbreaker/
    │       ├── CircuitBreakerApplication.java
    │       ├── breaker/
    │       │   ├── CircuitBreaker.java
    │       │   ├── CircuitBreakerState.java
    │       │   └── ExternalService.java
    │       ├── payment/
    │       │   ├── PaymentService.java
    │       │   ├── MockBankClient.java
    │       │   └── PaymentController.java
    │       └── config/
    │           └── CircuitBreakerConfig.java
    └── test/
        └── java/com/example/circuitbreaker/
            ├── breaker/
            │   └── CircuitBreakerTest.java
            └── payment/
                └── PaymentServiceTest.java
```

You may adjust the package structure, but keep the concepts clearly separated.

---

## Core Interview Problem

The existing `ExternalService` contract should be simple:

```java
public interface ExternalService {
    boolean call();
}
```

The candidate must implement a `CircuitBreaker` which wraps this service.

### Required behaviour

1. Initially the circuit is **CLOSED**.
2. In CLOSED state, calls are passed through to the external service.
3. A `false` response counts as a failure.
4. If **3 failures occur within a rolling 30-second window**, the circuit becomes **OPEN**.
5. While OPEN, calls must not invoke the external service.
6. After the circuit has been OPEN for **10 seconds**, allow one trial call.
7. This trial period is effectively **HALF_OPEN**.
8. If the trial call succeeds, transition to CLOSED and reset failure history.
9. If the trial call fails, remain OPEN and start a new 10-second cooldown.
10. Assume **single-threaded execution** for the initial exercise.

Use an explicit state machine:

```text
CLOSED --3 failures in 30s--> OPEN
OPEN --10s elapsed--> HALF_OPEN
HALF_OPEN --success--> CLOSED
HALF_OPEN --failure--> OPEN
```

### Initial values

The implementation should start conceptually as:

```text
state    = CLOSED
openedAt = null
failures = empty
```

`openedAt` is only meaningful while the circuit is OPEN.

When the circuit closes again, clear `openedAt`.

---

## Important Design Expectation

The exercise is intended to expose reasoning rather than framework knowledge.

The implementation should use a **rolling/sliding time window** for failures.

A simple expected approach is:

```java
Deque<Instant> failureTimes = new ArrayDeque<>();
```

Failures should be kept in chronological order.

When checking the current time, remove timestamps that are older than the 30-second window. Then inspect the remaining failure count.

The design should make it obvious why a `Deque` is appropriate:

- add the newest failure to the back
- remove expired failures from the front
- both operations are O(1)

Do not replace this with a database, Redis, Kafka, scheduled jobs, or external libraries.

---

## Make Time Testable

Do **not** make correctness depend on calls to `Instant.now()` scattered throughout the code.

Prefer dependency injection of a `Clock`, for example:

```java
private final Clock clock;
```

Then use:

```java
Instant.now(clock)
```

This makes tests deterministic and allows the candidate to reason about exact timestamps.

A system clock can be used in the application wiring, while tests can use a fixed or controllable clock.

Do not build an unnecessarily sophisticated fake-clock framework. A small test utility is enough.

---

## HTTP Layer

Provide a very small REST endpoint so the behaviour can be observed manually.

For example:

```text
POST /payments
```

The endpoint should eventually delegate to a service which calls the mocked external bank through the circuit breaker.

The mock bank should be configurable so that it can return success or failure.

Keep the HTTP API intentionally small. A simple example is enough:

```text
POST /payments?success=false
POST /payments?success=true
```

or an equivalent request body.

The HTTP layer exists only to make the system easier to visualise. The primary interview target remains the `CircuitBreaker` class and its unit tests.

---

## Suggested Domain Classes

### `CircuitBreakerState`

Use an enum:

```java
public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
```

### `CircuitBreaker`

Responsibilities:

- hold current state
- hold failure timestamps
- hold `openedAt`
- decide whether the external service can be called
- invoke the external service
- update state after the result

Do not mix HTTP concerns into this class.

### `ExternalService`

Simple interface used to demonstrate dependency inversion and mocking/stubbing.

### `MockBankClient`

A deliberately simple implementation of `ExternalService` whose result can be controlled for manual testing.

### `PaymentService`

Thin application service that calls the circuit breaker.

### `PaymentController`

Thin HTTP adapter.

---

## Error Semantics

For the initial exercise, keep semantics deliberately simple:

- `true` = external call succeeded
- `false` = external call failed

Do not introduce checked exceptions, retries, asynchronous execution, or custom failure taxonomies unless needed for a small, clearly documented extension.

The circuit breaker itself should remain easy to understand.

---

## Unit Tests

Tests are a major part of the simulator. Include tests covering at least:

### Basic CLOSED behaviour

- first successful call reaches the external service
- failed calls reach the external service while still CLOSED
- fewer than 3 recent failures does not open the circuit

### Rolling 30-second window

Test that:

- 3 failures inside 30 seconds open the circuit
- failures older than 30 seconds are discarded
- 3 failures spread outside the 30-second window do not open it

Example timeline:

```text
12:00:00 failure
12:00:10 failure
12:00:20 failure
```

At `12:00:20`, the circuit opens.

Counter-example:

```text
12:00:00 failure
12:00:20 failure
12:00:31 failure
```

The first failure is no longer in a 30-second window ending at `12:00:31`, so the threshold is not reached.

### OPEN behaviour

- calls made before 10 seconds do not reach the external service
- exactly/after the 10-second cooldown allows a trial

### HALF_OPEN behaviour

- successful trial closes the circuit
- failed trial returns/remains OPEN
- failed trial resets `openedAt` so another 10 seconds must pass

### Reset behaviour

After returning to CLOSED:

- failure history is empty
- `openedAt` is null

### Boundary decisions

Explicitly define and test the treatment of timestamps exactly on the boundary.

Choose a consistent rule, document it, and use it everywhere. A reasonable rule is:

```text
failure is considered expired when timestamp <= now - 30 seconds
```

and:

```text
trial is allowed when now >= openedAt + 10 seconds
```

Do not leave these boundaries ambiguous.

---

## README Requirements

Write a README that explains:

1. what the simulator is
2. the circuit breaker rules
3. the state machine
4. how to run tests
5. how to run the Spring Boot app
6. a couple of example HTTP calls
7. where the interview candidate should start reading

Explicitly tell the candidate that the intended starting point is:

```text
CircuitBreaker.java
CircuitBreakerTest.java
```

Do not give away the implementation in the README.

---

## Interview Simulation Mode

The resulting codebase should feel like an interviewer has handed the candidate an existing repository and said something similar to:

> "We've got an external dependency that occasionally fails. We'd like to protect ourselves from repeatedly calling it when it is unhealthy. Here's an existing service and a partially implemented circuit breaker. Talk me through how you'd approach it and then implement it."

Therefore:

- leave `CircuitBreaker.call()` incomplete or intentionally simplified
- leave a few TODOs in the candidate-facing implementation
- provide interfaces, domain objects, tests, and application wiring around it
- avoid comments that directly reveal the solution
- do not name the algorithm/data structure the candidate is expected to use inside the TODO

The surrounding code should compile except for the intentionally incomplete method only if the chosen setup still permits the candidate to run and inspect the rest of the project. Prefer a compilable starting point if practical by returning a placeholder value while clearly marking the method as incomplete.

---

## Optional Interview Variants

Create comments or a separate section in the README describing optional follow-up changes the interviewer could introduce after the initial solution is working.

Do not implement all of them unless very small.

Possible extensions:

1. Make it thread-safe.
2. Count exceptions as failures.
3. Make thresholds configurable.
4. Record metrics for OPEN/HALF_OPEN transitions.
5. Support a configurable failure window.
6. Allow multiple HALF_OPEN trial requests instead of one.
7. Add a recovery timeout configuration.

The initial implementation must remain simple.

---

## Code Quality Constraints

Use clean, ordinary Java rather than clever abstractions.

Prefer:

- constructor injection
- small methods
- descriptive names
- enums for finite state
- standard Java collections
- `Instant`, `Duration`, and `Clock`
- unit tests that describe behaviour

Avoid:

- Lombok unless already required
- heavy framework configuration
- unnecessary interfaces
- reflection tricks
- static mutable state
- sleeping in tests
- real network calls
- concurrent execution in the initial version

The goal is a codebase that an interview candidate can understand in a few minutes.

---

## Deliverable

Produce a fully runnable Gradle Spring Boot project in the current workspace.

Before finishing:

1. run the test suite
2. fix compilation/test failures
3. verify the application starts
4. verify the README instructions match the actual project
5. make sure the candidate-facing code still presents a realistic interview exercise rather than a completed solution

Do not add unrelated features.
