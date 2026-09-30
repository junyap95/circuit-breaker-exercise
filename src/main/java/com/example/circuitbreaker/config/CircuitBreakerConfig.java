package com.example.circuitbreaker.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.circuitbreaker.breaker.CircuitBreaker;
import com.example.circuitbreaker.breaker.CircuitBreakerProperties;
import com.example.circuitbreaker.payment.MockBankClient;

/**
 * Wires the single circuit breaker that guards the bank.
 *
 * <p>The {@link Clock} is a bean so that the application uses real time while tests
 * can hand the breaker a controllable clock.
 */
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CircuitBreaker bankCircuitBreaker(MockBankClient bankClient,
                                             Clock clock,
                                             CircuitBreakerProperties properties) {
        return new CircuitBreaker(bankClient, clock, properties);
    }
}
