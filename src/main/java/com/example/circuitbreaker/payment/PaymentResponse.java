package com.example.circuitbreaker.payment;

import com.example.circuitbreaker.breaker.CircuitBreakerState;

/**
 * Outcome of a payment attempt. The circuit state is included so that the
 * behaviour of the breaker can be observed from the HTTP API.
 */
public record PaymentResponse(String reference, PaymentStatus status, CircuitBreakerState circuitState) {
}
