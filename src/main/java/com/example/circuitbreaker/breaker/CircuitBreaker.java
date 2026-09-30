package com.example.circuitbreaker.breaker;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

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

    /**
     * Timestamps of failures seen while CLOSED, oldest first. Only entries inside the
     * failure window are kept; expired ones are dropped from the front on each failure.
     */
    private final Deque<Instant> recentFailures = new ArrayDeque<>();

    public CircuitBreaker(ExternalService externalService, Clock clock, CircuitBreakerProperties properties) {
        this.externalService = externalService;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Calls the external service, subject to the state of the circuit.
     */
    public CallResult call() {
        Instant now = now();

        boolean succeeded = externalService.call();

        if (!succeeded) {
            recordFailure(now);
        }
        return succeeded ? CallResult.SUCCESS : CallResult.FAILURE;
    }

    public CircuitBreakerState getState() {
        return state;
    }

    private void recordFailure(Instant now) {
        recentFailures.addLast(now);
        evictExpiredFailures(now);
        if (recentFailures.size() >= properties.failureThreshold()) {
            open(now);
        }
    }

    /** A failure has expired once it is {@code failureWindow} old or older. */
    private void evictExpiredFailures(Instant now) {
        Instant cutoff = now.minus(properties.failureWindow());
        while (!recentFailures.isEmpty() && !recentFailures.peekFirst().isAfter(cutoff)) {
            recentFailures.pollFirst();
        }
    }

    private void open(Instant now) {
        state = CircuitBreakerState.OPEN;
        openedAt = now;
        recentFailures.clear();
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
