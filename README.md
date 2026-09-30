# Circuit Breaker – Java Pair-Programming Exercise

A small Spring Boot service that takes payments through a (mocked) bank API. The bank is
unreliable, and the codebase contains a **partially implemented circuit breaker** that is
supposed to protect the service from hammering the bank while it is unhealthy.

This repository simulates what you might be handed in a 60-minute Java pair-programming
interview. Everything compiles and runs; the interesting logic is missing and clearly marked.

> "We've got an external dependency that occasionally fails. We'd like to stop calling it
> repeatedly while it's unhealthy. Here's the service and a partially implemented circuit
> breaker. Talk me through how you'd approach it, then implement it."

## Prerequisites

- JDK 17 or newer (`java -version`)
- Nothing else. The Gradle wrapper downloads Gradle on first use.

## Running the tests

```bash
./gradlew test
```

On `main` the suite is green but several tests are `@Disabled` with a `TODO(candidate)` message.
They describe behaviour the skeleton does not have. The exercise is finished when you can remove
every `@Disabled` and the suite still passes.

This branch (`feat/circuit-breaker-implementation`) is one candidate's completed attempt: every
test is enabled and the three open-ended cases have been decided and tested. See the commit
history for the incremental steps.

Run a single test class while iterating:

```bash
./gradlew test --tests 'com.example.circuitbreaker.breaker.CircuitBreakerTest'
```

## Running the application

```bash
./gradlew bootRun
```

The app starts on <http://localhost:8080>. If that port is taken, pass another one:
`./gradlew bootRun --args='--server.port=18080'`.

Poke it with curl:

```bash
# Take a payment (bank is healthy by default)
curl -s -X POST localhost:8080/payments \
     -H 'Content-Type: application/json' \
     -d '{"reference":"order-1","amount":25.00}'
# -> {"reference":"order-1","status":"APPROVED","circuitState":"CLOSED"}

# Make the bank start failing
curl -s -X PUT 'localhost:8080/mock-bank?healthy=false'

# Three declined payments in a row should open the circuit...
curl -s -X POST localhost:8080/payments -H 'Content-Type: application/json' -d '{"reference":"order-2","amount":25.00}'
curl -s -X POST localhost:8080/payments -H 'Content-Type: application/json' -d '{"reference":"order-3","amount":25.00}'
curl -s -X POST localhost:8080/payments -H 'Content-Type: application/json' -d '{"reference":"order-4","amount":25.00}'

# ...after which further payments should come back 503 UNAVAILABLE without touching the bank
curl -s -i -X POST localhost:8080/payments -H 'Content-Type: application/json' -d '{"reference":"order-5","amount":25.00}'

# Let the bank recover, wait 10 seconds, and the next payment is the trial call
curl -s -X PUT 'localhost:8080/mock-bank?healthy=true'
```

With the starting code the circuit never opens, so every call reaches the bank. Watch the
application log: `MockBankClient` logs each time it is actually called.

## Where to start reading

1. `src/main/java/com/example/circuitbreaker/breaker/CircuitBreaker.java` – the class you will
   change. Read the class Javadoc and the `TODO(candidate)` comments.
2. `src/test/java/com/example/circuitbreaker/breaker/CircuitBreakerTest.java` – the behavioural
   spec. Each `@Disabled` test is a step; enable them one at a time.
3. `breaker/ExternalService.java`, `breaker/CallResult.java`, `breaker/CircuitBreakerState.java`
   – the tiny contract the breaker sits between.
4. `payment/PaymentService.java` and `config/CircuitBreakerConfig.java` – how the breaker is
   used and wired. You should not need to change these.

The HTTP layer (`PaymentController`, `MockBankController`) exists only so the behaviour can be
observed manually. It is not the focus.

## The behaviour to implement

The breaker wraps an `ExternalService` whose `call()` returns `true` (success) or `false`
(failure). Thresholds come from `application.yml` (`circuit-breaker.*`) and are passed in as
`CircuitBreakerProperties`; tests use 3 failures / 30 s window / 10 s cooldown.

```text
                 3 failures within a 30 s sliding window
   CLOSED  ───────────────────────────────────────────────►  OPEN
     ▲                                                        │
     │ trial succeeds                            10 s elapsed │
     │                                                        ▼
     └──────────────────────────  HALF_OPEN  ◄────────────────┘
                                     │
                                     │ trial fails
                                     ▼
                                   OPEN  (fresh 10 s cooldown)
```

1. The circuit starts `CLOSED`. Calls pass straight through to the external service.
2. A `false` result is a failure. Failures are counted over a **sliding window of the last
   30 seconds**, measured from "now" – not fixed 30-second buckets.
3. When the third failure lands inside that window, the circuit becomes `OPEN`.
4. While `OPEN`, `call()` returns `CallResult.REJECTED` **without invoking the external
   service**.
5. Once the circuit has been open for 10 seconds, the next call is a **trial** (`HALF_OPEN`)
   and is allowed through.
6. If the trial succeeds the circuit becomes `CLOSED` again and the failure history is
   forgotten. If it fails the circuit is `OPEN` again with a fresh 10-second cooldown.
7. The breaker is used from a single thread. You do not need to make it thread-safe, but
   expect to be asked what would change if you had to.

### Boundary rules

Timing boundaries are easy to get wrong, so this project fixes them:

- A failure is **no longer counted** once it is 30 seconds old or older, i.e. it expires when
  `failureTime <= now - 30s`.
- A trial call is **allowed** as soon as `now >= openedAt + 10s`.

### Constraints

- Read the time only via the injected `java.time.Clock` (`Instant.now(clock)`), never
  `Instant.now()`, or the tests cannot control time.
- Plain Java collections only. No Resilience4j, no scheduling, no threads.
- Keep `call()` short; extract private helpers with descriptive names.

## Things left deliberately open (and how this branch decided them)

On `main`, the bottom of `CircuitBreakerTest` has three empty tests to decide on and write.
This branch chose:

- **Boundary**: a failure exactly 30 s old has expired (`<=` rule from above); 29 s old still counts.
- **Success while `CLOSED`**: does not reset the failure history. The rule is "3 failures in
  30 s", not "3 consecutive failures", so a flapping dependency still trips the breaker.
- **Exceptions**: a `RuntimeException` from the service counts as a failure (including during the
  trial) and is rethrown, so the caller still sees the original error.

There is no single right answer to the last two; the tests document the choice.

## Possible follow-ups an interviewer might add

Only after the core behaviour is working:

1. Make the breaker safe to call from multiple threads.
2. Treat exceptions thrown by the external service as failures.
3. Allow more than one trial call while `HALF_OPEN`.
4. Expose state transitions as log lines or metrics.
5. Add a `GET /circuit-breaker` endpoint that reports the current state.
6. Support a minimum number of calls before the failure rate is evaluated.

See [`INTERVIEW_QUESTIONS.md`](INTERVIEW_QUESTIONS.md) for the questions you are likely to be
asked along the way, and [`REPOSITORY_NOTES.md`](REPOSITORY_NOTES.md) for the layout of the
project and the reasoning behind its design.
