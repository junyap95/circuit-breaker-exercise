package com.example.circuitbreaker.breaker;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning knobs for the circuit breaker. Bound from {@code circuit-breaker.*} in
 * {@code application.yml}; tests construct this directly.
 *
 * @param failureThreshold number of failures within {@code failureWindow} that opens the circuit
 * @param failureWindow    how far back failures are counted
 * @param openDuration     how long the circuit stays open before a trial call is allowed
 */
@ConfigurationProperties(prefix = "circuit-breaker")
public record CircuitBreakerProperties(
        int failureThreshold,
        Duration failureWindow,
        Duration openDuration) {

    public CircuitBreakerProperties {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be at least 1");
        }
        if (failureWindow == null || failureWindow.isNegative() || failureWindow.isZero()) {
            throw new IllegalArgumentException("failureWindow must be positive");
        }
        if (openDuration == null || openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
    }
}
