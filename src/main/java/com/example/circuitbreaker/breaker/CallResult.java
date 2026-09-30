package com.example.circuitbreaker.breaker;

/**
 * The outcome of asking the circuit breaker to call the external service.
 */
public enum CallResult {

    /** The external service was called and reported success. */
    SUCCESS,

    /** The external service was called and reported failure. */
    FAILURE,

    /** The circuit was open, so the external service was <em>not</em> called. */
    REJECTED
}
