package com.example.circuitbreaker.breaker;

/**
 * The states a circuit breaker can be in.
 *
 * <pre>
 * CLOSED    - healthy; calls pass through to the external service.
 * OPEN      - unhealthy; calls are rejected without touching the external service.
 * HALF_OPEN - probing; a single trial call is allowed through to see if the
 *             external service has recovered.
 * </pre>
 */
public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
