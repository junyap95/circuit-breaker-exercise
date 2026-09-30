package com.example.circuitbreaker.breaker;

import java.time.Clock;
import java.time.Instant;

/**
 * Protects callers from an unhealthy {@link ExternalService}.
 *
 * <p>Intended behaviour (see README for the full rules):
 * <ul>
 *   <li>While {@link CircuitBreakerState#CLOSED}, calls pass through to the service.</li>
 *   <li>If {@code failureThreshold} failures happen within {@code failureWindow}
 *       (a sliding window ending "now"), the circuit opens.</li>
 *   <li>While {@link CircuitBreakerState#OPEN}, calls are rejected without touching
 *       the service until {@code openDuration} has elapsed since it opened.</li>
 *   <li>Once the cooldown has elapsed, a single trial call is let through
 *       ({@link CircuitBreakerState#HALF_OPEN}). Success closes the circuit and
 *       clears any failure history; failure re-opens it with a fresh cooldown.</li>
 * </ul>
 *
 * <p>This class is used from a single thread in the exercise; thread safety is a
 * follow-up discussion, not a requirement of the initial implementation.
 *
 * <p>Always read the time through {@link #clock} (e.g. {@code Instant.now(clock)}) so
 * that tests can control it.
 */
public class CircuitBreaker {

    private final ExternalService externalService;
    private final Clock clock;
    private final CircuitBreakerProperties properties;

    private CircuitBreakerState state = CircuitBreakerState.CLOSED;

    /** When the circuit last transitioned to OPEN. Only meaningful while OPEN. */
    private Instant openedAt = null;

    // TODO(candidate): you will need somewhere to remember recent failures so that you
    // can count how many happened within the failure window. Choose a representation
    // and be ready to talk about why you picked it.

    public CircuitBreaker(ExternalService externalService, Clock clock, CircuitBreakerProperties properties) {
        this.externalService = externalService;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Calls the external service, subject to the state of the circuit.
     *
     * <p>TODO(candidate): this is currently a plain pass-through that ignores the circuit
     * state entirely. It needs to:
     * <ol>
     *   <li>refuse the call (returning {@link CallResult#REJECTED} and <em>not</em> touching
     *       the external service) while the circuit is open and the cooldown has not elapsed;</li>
     *   <li>record failures and open the circuit once the threshold is reached within the window;</li>
     *   <li>let a single trial call through after the cooldown, then close or re-open the
     *       circuit depending on the result.</li>
     * </ol>
     *
     * <p>Keep this method small; push the details into well-named private helpers.
     */
    public CallResult call() {
        boolean succeeded = externalService.call();
        return succeeded ? CallResult.SUCCESS : CallResult.FAILURE;
    }

    public CircuitBreakerState getState() {
        return state;
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
