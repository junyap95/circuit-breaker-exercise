package com.example.circuitbreaker.breaker;

/**
 * A dependency that lives outside this process and may be unhealthy.
 *
 * <p>The contract is deliberately minimal: the call either succeeded ({@code true})
 * or it did not ({@code false}). Implementations are expected to report failures
 * through the return value rather than by throwing.
 */
public interface ExternalService {

    boolean call();
}
