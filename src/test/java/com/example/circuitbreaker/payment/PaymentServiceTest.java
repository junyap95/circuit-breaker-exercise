package com.example.circuitbreaker.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.circuitbreaker.breaker.CallResult;
import com.example.circuitbreaker.breaker.CircuitBreaker;
import com.example.circuitbreaker.breaker.CircuitBreakerState;

/**
 * {@link PaymentService} only maps breaker outcomes to payment statuses, so the
 * breaker is mocked here and its real behaviour is covered in {@code CircuitBreakerTest}.
 */
class PaymentServiceTest {

    private static final PaymentRequest REQUEST = new PaymentRequest("order-42", new BigDecimal("19.99"));

    private CircuitBreaker breaker;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        breaker = mock(CircuitBreaker.class);
        paymentService = new PaymentService(breaker);
    }

    @Test
    void successfulBankCallIsApproved() {
        when(breaker.call()).thenReturn(CallResult.SUCCESS);
        when(breaker.getState()).thenReturn(CircuitBreakerState.CLOSED);

        PaymentResponse response = paymentService.pay(REQUEST);

        assertThat(response.reference()).isEqualTo("order-42");
        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.circuitState()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void failedBankCallIsDeclined() {
        when(breaker.call()).thenReturn(CallResult.FAILURE);
        when(breaker.getState()).thenReturn(CircuitBreakerState.CLOSED);

        PaymentResponse response = paymentService.pay(REQUEST);

        assertThat(response.status()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    void rejectedCallIsReportedAsUnavailable() {
        when(breaker.call()).thenReturn(CallResult.REJECTED);
        when(breaker.getState()).thenReturn(CircuitBreakerState.OPEN);

        PaymentResponse response = paymentService.pay(REQUEST);

        assertThat(response.status()).isEqualTo(PaymentStatus.UNAVAILABLE);
        assertThat(response.circuitState()).isEqualTo(CircuitBreakerState.OPEN);
    }
}
