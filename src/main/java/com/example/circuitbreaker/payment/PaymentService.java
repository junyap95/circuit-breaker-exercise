package com.example.circuitbreaker.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.circuitbreaker.breaker.CallResult;
import com.example.circuitbreaker.breaker.CircuitBreaker;

/**
 * Takes payments by calling the bank through the circuit breaker.
 *
 * <p>Deliberately thin: it translates the breaker's {@link CallResult} into a
 * domain-level {@link PaymentStatus} and nothing more.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final CircuitBreaker bankCircuitBreaker;

    public PaymentService(CircuitBreaker bankCircuitBreaker) {
        this.bankCircuitBreaker = bankCircuitBreaker;
    }

    public PaymentResponse pay(PaymentRequest request) {
        CallResult result = bankCircuitBreaker.call();
        PaymentStatus status = toStatus(result);

        log.info("Payment {} for {} -> {} (circuit {})",
                request.reference(), request.amount(), status, bankCircuitBreaker.getState());

        return new PaymentResponse(request.reference(), status, bankCircuitBreaker.getState());
    }

    private static PaymentStatus toStatus(CallResult result) {
        return switch (result) {
            case SUCCESS -> PaymentStatus.APPROVED;
            case FAILURE -> PaymentStatus.DECLINED;
            case REJECTED -> PaymentStatus.UNAVAILABLE;
        };
    }
}
