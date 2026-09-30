package com.example.circuitbreaker.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.circuitbreaker.breaker.ExternalService;

/**
 * Stand-in for a real bank API. Its health can be toggled at runtime (see
 * {@link MockBankController}) so that failures can be simulated without a network.
 *
 * <p>{@code healthy} is volatile only because HTTP requests arrive on different
 * threads; the circuit breaker itself is still exercised single-threadedly.
 */
@Component
public class MockBankClient implements ExternalService {

    private static final Logger log = LoggerFactory.getLogger(MockBankClient.class);

    private volatile boolean healthy = true;

    @Override
    public boolean call() {
        log.info("Bank called; healthy={}", healthy);
        return healthy;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
}
