# Interview Follow-Up Questions

Questions an interviewer is likely to ask while you implement the circuit breaker or once it is
working. For each one: what is really being tested, and the points a good answer touches.
These are notes to understand the concepts, not scripts to recite.

Work through them after you have a first implementation; many of them make more sense once you
have hit the problem yourself.

---

## Design and state

### Why use a state machine?

**Testing:** whether you can name the shape of the problem rather than just code around it.

- The breaker's behaviour depends on *which mode it is in*, not just on the latest input. That
  is the definition of a state machine.
- Naming the states makes the transitions explicit and finite. You can draw it on a whiteboard,
  enumerate the transitions, and write one test per arrow.
- The alternative (a pile of booleans and counters) tends to produce impossible combinations
  like "open but also counting failures towards opening".

### Why `CLOSED`, `OPEN` and `HALF_OPEN`?

**Testing:** do you understand what each state is *for*.

- `CLOSED`: healthy, calls flow (the electrical metaphor: closed circuit = current flows).
- `OPEN`: unhealthy, calls are rejected fast so the caller fails quickly and the dependency
  gets breathing room.
- `HALF_OPEN`: the probe. Without it you have two bad options: stay open forever, or slam
  straight back to closed and let full traffic through before you know the dependency has
  recovered.
- Be ready to say that `HALF_OPEN` can be *implicit* (an `OPEN` circuit whose cooldown has
  elapsed) or *explicit* (a real enum value you transition to). Either is defensible; explicit
  is easier to read and log.

### What should `openedAt` contain initially? Why can it be `null`?

**Testing:** whether you think about the meaning of a field, not just its type.

- Initially the circuit has never opened, so there is no meaningful value. `null` (or
  `Optional.empty()`) says exactly that.
- A sentinel like `Instant.EPOCH` or `Instant.MIN` would "work" but lies: `openedAt.plus(10s)`
  would be in the distant past and the cooldown check would silently pass.
- Key discipline: `openedAt` is only valid while `state == OPEN`. Set it when you open, clear it
  when you close, and never read it in any other state. Say this out loud; it shows you are
  reasoning about invariants.

### What is the difference between checking the state on each call versus using a timer to flip it?

**Testing:** awareness of lazy versus eager transitions.

- This exercise uses the lazy approach: the state only changes when someone calls. Nothing
  happens in the background, which is simpler, has no threads, and is trivially testable with a
  fake clock.
- A timer-based approach transitions `OPEN -> HALF_OPEN` at the right moment even with no
  traffic, and lets `getState()` be accurate at all times, at the cost of scheduling and
  concurrency.
- Note the consequence: with lazy transitions, `getState()` may say `OPEN` for a circuit whose
  cooldown has elapsed. Is that a bug or an accepted trade-off? Have an opinion.

---

## The sliding window

### How does the 30-second sliding window work?

**Testing:** whether you understand the difference between a sliding window and fixed buckets.

- "Failures in the last 30 seconds" means the window ends *now* and starts `now - 30s`. It moves
  with every call.
- Fixed buckets (12:00:00–12:00:30, 12:00:30–12:01:00) are simpler but have the boundary
  problem: three failures at 12:00:29, 12:00:31 and 12:00:32 span two buckets and would never
  trip the breaker.
- Implementation: keep the timestamps of recent failures, discard those older than the window,
  count what is left.

### Why store timestamps in a `Deque`?

**Testing:** data-structure choice with justification.

- Failures arrive in time order, so the collection is naturally sorted by construction.
- You always add the newest at one end and expire the oldest from the other end. That is a
  queue. `ArrayDeque` gives O(1) at both ends without the overhead of `LinkedList`.
- Alternatives and why they are worse here: `ArrayList` (removing from the front is O(n)),
  `PriorityQueue` (pointless, data is already ordered), a single counter plus "window start"
  (that is fixed buckets in disguise).
- Bonus: because the deque never holds more than `threshold` entries if you clear it when the
  circuit opens, memory is bounded.

### What is the time and space complexity?

**Testing:** whether you can analyse your own code.

- Per call: amortised O(1). Each timestamp is added once and removed once, so the expiry loop
  costs O(1) amortised even though a single call might pop several entries.
- Space: O(k) where k is the number of failures inside the window. If you cap by clearing on
  open, k is at most the threshold.
- If asked about a "cleverer" version: you can also stop early once you have found the
  threshold; it does not change the asymptotics.

### What happens when an old failure leaves the window?

**Testing:** do you understand that the window is evaluated relative to the current time.

- It simply stops being counted. If you had 3 failures and one expires, you now have 2 and the
  circuit would not open on the *next* failure until there are 3 in-window again.
- Point out when you expire: lazily on the next call (fine, cheap) versus eagerly on a timer
  (unnecessary here).
- Corner case worth mentioning: expired entries only matter while `CLOSED`. Once `OPEN`, the
  history is irrelevant and can be cleared.

### What if a failure lands exactly on the boundary?

**Testing:** whether you notice ambiguity and resolve it deliberately.

- `now - 30s` compared with a timestamp of exactly `now - 30s`: is that inside or outside? Both
  are defensible; what is not defensible is leaving it to whichever comparison operator you
  happened to type.
- This project's rule: expired when `timestamp <= now - 30s`, and a trial is allowed when
  `now >= openedAt + 10s`. Write a test that pins each rule down.
- Mention that `Instant.isAfter`/`isBefore` are strict, so `!isAfter` is "less than or equal".
  Off-by-one bugs around this are extremely common in real code.

---

## Success, failure and exceptions

### What happens if the external service succeeds after previous failures?

**Testing:** whether you have thought about the happy path interacting with the failure history.

- In `CLOSED`, a success is just a success: it does not change the state. The question is
  whether it should affect the failure history (next question).
- In `HALF_OPEN`, a success is the recovery signal: close the circuit and clear everything.

### Should a successful call reset the failure history?

**Testing:** trade-off reasoning; there is no single right answer.

- "Consecutive failures" semantics: a success resets the count. Good for detecting hard outages,
  but a flapping dependency (fail, ok, fail, ok, fail) never trips the breaker.
- "Failures in a window" semantics (what this exercise asks for): successes are ignored, so
  intermittent failure still trips it. This is closer to what production libraries do; they
  usually go further and use a *failure rate* over a minimum number of calls.
- Whichever you choose, write a test that documents it.

### What if the service throws an exception instead of returning `false`?

**Testing:** whether you think about failure modes beyond the happy contract.

- Currently an exception propagates through `call()` and the breaker does not record anything,
  so a dependency that throws could never trip the breaker. That is a real bug in real systems.
- Options: catch, record a failure, and rethrow; catch and return `FAILURE`; or leave it to the
  `ExternalService` implementation to translate exceptions into `false`.
- Be careful about *which* exceptions. A timeout or connection refused clearly means "dependency
  unhealthy". An `IllegalArgumentException` caused by a bad request from *us* arguably should not
  count against the dependency.

### Application-level failure versus exception: what is the difference?

**Testing:** vocabulary and judgement about what "failure" means for a breaker.

- An application-level failure is a well-formed response that says "no" (payment declined,
  validation error, HTTP 4xx). The dependency is *healthy*; it just did not do what we wanted.
- An exception or infrastructure failure (timeout, 5xx, connection reset) means the dependency
  itself is struggling. That is what a circuit breaker is meant to react to.
- Counting business declines as breaker failures means a run of genuinely bad cards would take
  your payment path offline. Discuss whether `false` in this exercise is really the right
  signal, and what a richer `ExternalService` contract might look like.

---

## Concurrency

### What happens if multiple threads call the breaker simultaneously?

**Testing:** whether you can spot the races in your own code.

- Two threads read `state == CLOSED`, both call the service, both record a failure and both
  evaluate the threshold: harmless-ish, but the deque itself is not thread-safe and can corrupt.
- Two threads see the cooldown has elapsed and *both* make a trial call. That defeats the
  purpose of `HALF_OPEN` (one probe), and with many threads it is a thundering herd.
- Visibility: without `volatile`/synchronisation another thread may not see the updated state
  at all.

### Would it need synchronisation or locking?

**Testing:** awareness of the tools and their costs.

- Simplest correct answer: make `call()` `synchronized` (or guard the state with a
  `ReentrantLock`). The critical section is tiny, but it must not include the external call
  itself or you have serialised all traffic to the dependency.
- Split it into: lock -> decide (reject / allow / trial) -> unlock -> call service -> lock ->
  record result -> unlock.
- Lock-free alternatives: `AtomicReference<State>` with compare-and-set for the
  `OPEN -> HALF_OPEN` transition guarantees exactly one trial thread; `AtomicInteger` counters
  for fixed buckets. The sliding-window deque is the awkward part; libraries use ring buffers.

---

## Production concerns

### What would change for a production implementation?

**Testing:** whether you know the gap between an interview exercise and a real component.

- Thread safety (above). Failure *rate* with a minimum call count rather than a raw count. A
  configurable number of permitted trial calls. Slow calls counted as failures. Separate
  handling for exception types. Metrics and state-change events. Per-dependency instances.
- Most importantly: you would probably not write it. Use Resilience4j (or the platform's
  equivalent) and spend your effort on choosing thresholds and fallbacks.

### How would you test time-dependent behaviour reliably?

**Testing:** test design maturity.

- Never `Thread.sleep` in tests: slow, flaky, and it does not let you test "exactly 30 seconds".
- Inject a `Clock`. Tests use a controllable clock and advance it explicitly; production uses
  `Clock.systemUTC()`. This repository does exactly that with `MutableClock`.
- Write tests around the boundaries (29 s, 30 s, 31 s; 9 s, 10 s), not just "a long time".

### Would you inject a `Clock` rather than call `Instant.now()` directly?

**Testing:** whether you recognise a hidden dependency.

- Yes. `Instant.now()` is a static call to a global; it is an untestable dependency in disguise.
- `Clock` is the JDK's built-in seam for this. `Instant.now(clock)` costs nothing in production
  and makes tests deterministic.
- Same principle applies to random numbers, environment variables and the file system.

### How might you expose metrics or logs?

**Testing:** operability awareness.

- Log every state transition at INFO with the reason ("OPEN after 3 failures in 30s",
  "CLOSED after successful trial"). Do not log every rejected call at INFO or you flood the logs
  precisely when the system is under stress.
- Metrics: a gauge for current state, counters for calls by outcome (success / failure /
  rejected), counter for transitions. With Spring Boot that means Micrometer and
  `/actuator/metrics` or `/actuator/health`.
- Alert on "circuit open for longer than N minutes", not on the first opening.

### What happens if the application restarts while the circuit is open?

**Testing:** understanding of where the state lives.

- The state is in memory, so it is lost. The new instance starts `CLOSED` and will make real
  calls to the dependency until it re-learns that it is unhealthy.
- Usually acceptable: it is a small burst, and the breaker re-opens within a few failures.
- If it matters, you could persist state or start `HALF_OPEN`, but that adds complexity for a
  marginal gain. Say why you probably would not.

### Would breaker state normally be local to one instance? What about several instances?

**Testing:** distributed-systems intuition without over-engineering.

- Yes, local. Each instance observes its own failures and makes its own decision. This is how
  Resilience4j, Hystrix and most service meshes behave.
- With N instances, each has to see the threshold independently, so the dependency receives up
  to N× the failures before everyone is open, and instances open/close at different times.
  That is normally fine and even desirable (no coordination, no single point of failure).
- Threshold tuning should account for per-instance traffic, not aggregate traffic.

### Would Redis or another shared store ever be appropriate?

**Testing:** knowing when *not* to reach for infrastructure.

- Rarely. A shared store adds a network hop on the hot path, a new failure mode (what happens
  when Redis is down?), and coordination latency.
- Cases where it is considered: very many low-traffic instances where no single instance sees
  enough failures; or when the decision to stop calling must be global and immediate (e.g. a
  partner has asked you to stop). Even then, a feature flag or config push is often simpler.
- Good answer shape: "default no; here is the specific situation where I would reconsider".

### How would you prevent a large number of requests overwhelming a recovering dependency?

**Testing:** do you understand what `HALF_OPEN` is protecting against.

- That is exactly the job of `HALF_OPEN`: allow one (or a small fixed number of) trial call(s)
  and reject the rest until the trial reports back.
- In a multi-threaded implementation, guaranteeing "only one" needs an atomic transition.
- Beyond the breaker: ramp traffic gradually after closing, add jitter to the cooldown so that
  many instances do not all probe at the same instant, and use a bulkhead (bounded concurrency)
  in front of the dependency.

---

## Related patterns

### How does this differ from retry logic?

**Testing:** whether you see that they solve opposite problems.

- Retry says "try again, it might work". A breaker says "stop trying, it will not work".
- Retries without a breaker amplify outages: every failed request becomes 3 requests, right when
  the dependency is least able to cope.
- They compose: retry inside the breaker for transient blips; the breaker stops the retries once
  the failure is sustained. Order matters; be able to say which wraps which and why.

### How does a circuit breaker differ from a rate limiter?

**Testing:** vocabulary precision.

- A rate limiter caps *how many* calls go through per unit time regardless of outcome; it
  protects against too much load.
- A breaker reacts to *outcomes*; it protects against calling something that is broken. A
  healthy dependency is never throttled by a breaker.
- Both involve time windows, which is why they get confused. The sliding-window logic you wrote
  is reused almost verbatim in a sliding-window rate limiter.

### Trade-offs: simple in-memory implementation versus a library such as Resilience4j?

**Testing:** engineering judgement about build versus buy.

- Hand-written: ~80 lines, fully understood, no dependency, trivially testable. Fine for a
  single, well-understood use case, and excellent for an interview.
- Library: battle-tested concurrency, failure-rate and slow-call semantics, configurable
  half-open permits, event listeners, Micrometer integration, Spring Boot starter and
  annotations, plus retry / bulkhead / rate-limiter / time-limiter that compose. Cost: a
  dependency, more configuration surface, and behaviour you have to learn rather than read.
- Good answer: "in a real service I would use Resilience4j, and I would want to understand what
  we just built so that I can configure it sensibly."
