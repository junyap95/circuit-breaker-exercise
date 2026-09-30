# Repository Notes

How the project is laid out, why it is shaped the way it is, and where it deliberately departs
from the original agent brief (`circuit-breaker-agent-instructions.md`).

## Repository structure

```text
circuit-breaker/
├── build.gradle                          Spring Boot 3.5 / Java 17 toolchain / JUnit 5
├── settings.gradle
├── gradlew, gradlew.bat, gradle/wrapper/ Gradle 8.14.3 wrapper (no local Gradle needed)
├── README.md                             Candidate-facing: how to run, where to start, the rules
├── INTERVIEW_QUESTIONS.md                Candidate-facing: follow-up questions and what they test
├── REPOSITORY_NOTES.md                   This file: structure, design decisions, deviations
├── circuit-breaker-agent-instructions.md The original brief (see "Spoiler warning" below)
└── src/
    ├── main/
    │   ├── java/com/example/circuitbreaker/
    │   │   ├── CircuitBreakerApplication.java   Spring Boot entry point
    │   │   ├── breaker/                         <-- the exercise lives here
    │   │   │   ├── CircuitBreaker.java          Skeleton with TODO(candidate) markers
    │   │   │   ├── CircuitBreakerState.java     CLOSED / OPEN / HALF_OPEN
    │   │   │   ├── CallResult.java              SUCCESS / FAILURE / REJECTED
    │   │   │   ├── CircuitBreakerProperties.java threshold, window, cooldown (record)
    │   │   │   └── ExternalService.java         boolean call()
    │   │   ├── payment/                         Thin domain that uses the breaker
    │   │   │   ├── PaymentService.java          CallResult -> PaymentStatus
    │   │   │   ├── PaymentController.java       POST /payments
    │   │   │   ├── PaymentRequest.java          record
    │   │   │   ├── PaymentResponse.java         record (includes circuitState)
    │   │   │   ├── PaymentStatus.java           APPROVED / DECLINED / UNAVAILABLE
    │   │   │   ├── MockBankClient.java          ExternalService with a health toggle
    │   │   │   └── MockBankController.java      PUT /mock-bank?healthy=...
    │   │   └── config/
    │   │       └── CircuitBreakerConfig.java    Clock bean + CircuitBreaker bean
    │   └── resources/
    │       └── application.yml                  circuit-breaker.* thresholds
    └── test/java/com/example/circuitbreaker/
        ├── breaker/
        │   ├── CircuitBreakerTest.java          Behavioural spec (partly @Disabled)
        │   ├── MutableClock.java                Controllable Clock for tests
        │   └── StubExternalService.java         Scriptable ExternalService with call count
        └── payment/
            ├── PaymentServiceTest.java          Mapping logic, breaker mocked
            └── PaymentApiTest.java              @SpringBootTest + MockMvc; proves the app boots
```

## State of the starting point

- `./gradlew build` is green: 9 tests pass, 12 are skipped.
- `CircuitBreaker.call()` is a plain pass-through. `state` never changes, `openedAt` is never
  set, and there is no failure history. The unused fields and the `now()` helper are
  intentional signposts.
- The 12 skipped tests are the specification. Nine are fully written and merely `@Disabled`
  ("enable once ..."). Three are empty scaffolds for the candidate to decide on and write.
- The spec tests were validated against a reference implementation (kept out of the repo): all
  pass with a correct breaker, and the positive ones fail against the skeleton with a useful
  message. Negative tests ("old failures are not counted") pass trivially on the skeleton, which
  is inherent to negative tests; the fixtures for `while OPEN` and `trial call` assert their
  precondition so they fail for the right reason ("circuit never opened") rather than by accident.

## Design decisions

### Candidate-facing surface is intentionally small
Three enums/interfaces, one record and one class in `breaker/`. Everything a candidate needs
to read before writing code fits on one screen. The payment package is there so the breaker is
used by *something*, and so "why does the service care about REJECTED vs FAILURE?" is a
question with a concrete answer.

### `Clock` is injected
`CircuitBreakerConfig` publishes `Clock.systemUTC()` as a bean; tests hand the breaker a
`MutableClock`. The skeleton already provides `now()` reading from the clock so the candidate
is nudged away from `Instant.now()` without being told why; that comes up in discussion.

### The failure-history field is not declared
The brief's suggested class shape includes a `Deque<Instant>`. Choosing that structure and
justifying it is one of the most valuable five minutes of the interview, so the skeleton
leaves only a `TODO` where the field should go. `INTERVIEW_QUESTIONS.md` still covers the
`Deque` reasoning because the candidate should arrive there themselves and then check.

### `HALF_OPEN` exists in the enum but is not asserted by tests
Whether the breaker moves to an explicit `HALF_OPEN` state or treats "OPEN with elapsed
cooldown" as implicitly half-open is left to the candidate. Tests observe behaviour (did the
call reach the service? what is the state *after* the trial?) rather than the intermediate
state, so both designs pass. This is a deliberate discussion hook (lazy vs eager transitions).

### No `getOpenedAt()` accessor
"After closing, `openedAt` is null" is tested indirectly: a closed circuit that still had a
stale `openedAt` would misbehave on the next open/cooldown cycle, which
`failedTrialStartsAFreshCooldown` and `successfulTrialClearsTheFailureHistory` catch. Exposing
internals purely for tests is a smell worth discussing, not modelling.

### Hand-rolled stub for the breaker, Mockito for the service
`StubExternalService` is a ten-line fake with an invocation counter; it makes "the service was
not called" a first-class assertion and shows the candidate a test double they could have
written themselves. `PaymentServiceTest` mocks the concrete `CircuitBreaker` with Mockito so it
is independent of whether the breaker has been implemented yet.

### `MockBankClient.healthy` is `volatile`
HTTP requests arrive on different Tomcat threads, and the toggle endpoint and the payment
endpoint would otherwise have a visibility problem. This is the only concession to concurrency
and it is called out in the Javadoc so nobody mistakes it for the breaker being thread-safe.

### Validation lives in `CircuitBreakerProperties`
The compact record constructor rejects nonsensical thresholds and durations. It keeps the
breaker free of argument checking and gives the candidate an example of validating at the edge.

## Deviations from the brief

| # | Brief said | This repo does | Why |
|---|------------|----------------|-----|
| 1 | `CircuitBreaker.call()` returns `boolean` | Returns `CallResult` (`SUCCESS` / `FAILURE` / `REJECTED`) | With a boolean the caller cannot tell "the bank declined" from "we never asked the bank". Real callers care (retry later vs. do not retry), tests can assert rejection directly instead of only via call counts, and the HTTP layer can return 503 vs 200 honestly. It also sets up the application-level-failure vs. infrastructure-failure discussion. |
| 2 | Configurable thresholds listed as a *follow-up* extension | `CircuitBreakerProperties` bound from `application.yml` from day one | Hard-coding `3`, `30s`, `10s` in a Spring service is not what a candidate would meet at work, and passing values in makes tests self-describing. Cost is a 20-line record. The follow-up list was topped up with other extensions instead. |
| 3 | Tests should cover the behaviour; starting point should compile | Full spec tests are present but `@Disabled("TODO(candidate) ...")`; three further tests are empty scaffolds | A red build on handover wastes interview minutes and makes candidates doubt the environment. `@Disabled` keeps `./gradlew build` green while making the roadmap explicit (grep for `TODO(candidate)`). Leaving a few tests entirely to the candidate keeps "write and run tests" a genuine part of the exercise rather than a checkbox. |
| 4 | "Explicitly define and test the boundary" | The rule is defined in the README; the boundary *test* is one of the scaffolds | Stating the rule removes ambiguity from the spec; writing the test that pins it is exactly the kind of small, precise task that reveals whether a candidate reads specs carefully. |
| 5 | `POST /payments?success=false` to control the mock bank | `PUT /mock-bank?healthy=false` plus a normal `POST /payments` with a JSON body | Test-control parameters on the production endpoint teach the wrong habit. Toggling the fake dependency separately mirrors how a stubbed downstream is driven in real environments and keeps the payment API clean. |
| 6 | Separate endpoint to observe breaker state (implied) | `circuitState` is included in every `PaymentResponse` | One fewer controller; the state is visible exactly where you are looking when you curl. Adding `GET /circuit-breaker` is listed as a small follow-up. |
| 7 | `PaymentService` is "thin" | Still thin, but maps to `APPROVED` / `DECLINED` / `UNAVAILABLE` and the controller returns 503 for `UNAVAILABLE` | Gives the breaker a consumer whose behaviour is observably different per outcome, which makes the `CallResult` decision (#1) concrete rather than theoretical. |
| 8 | A `contextLoads` style test | `PaymentApiTest` (`@SpringBootTest` + MockMvc) | Proves the context loads *and* that wiring, JSON and the mock-bank toggle work end to end, for the same startup cost. |
| 9 | `Deque<Instant> failureTimes` shown in the class shape | Field omitted from the skeleton | See "The failure-history field is not declared" above. The brief itself asked not to name the data structure in the TODO; omitting the field follows that intent through. |
| 10 | Brief left in whatever location | Left at repo root, unmodified | It is the source of record for the exercise. See spoiler warning below. |

Things from the brief that were kept as-is: Java 17, Spring Boot 3.x, Gradle, JUnit 5,
constructor injection, no Lombok, no Resilience4j, no scheduling, single-threaded breaker,
`Clock` injection, `Instant`/`Duration`, an explicit `CircuitBreakerState` enum, package split
into `breaker` / `payment` / `config`, README pointing at `CircuitBreaker.java` and
`CircuitBreakerTest.java` as the starting point, and no solution in the README.

## Spoiler warning

`circuit-breaker-agent-instructions.md` names the intended data structure and sketches the
algorithm. `INTERVIEW_QUESTIONS.md` does too, by necessity. If this repository is handed to
someone for a *blind* attempt, remove or move those two files first and hand them over
afterwards. For self-study, read the code and tests first, attempt the implementation, and only
then read the questions file.

## Toolchain notes

- No Gradle or Maven was installed locally, so the wrapper was bootstrapped by hand:
  `gradle-wrapper.jar`, `gradlew` and `gradlew.bat` come from the Gradle 8.14.3 release tag and
  `gradle-wrapper.properties` points at the matching distribution. `./gradlew` behaves exactly as
  a generated wrapper would.
- Spring Boot 3.5.6 with the Java 17 toolchain; runs on any JDK 17+.
- `spring-boot-starter-web` is the only runtime dependency. `spring-boot-starter-test` brings
  JUnit 5, AssertJ, Mockito and MockMvc.
