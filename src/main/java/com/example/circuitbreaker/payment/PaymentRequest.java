package com.example.circuitbreaker.payment;

import java.math.BigDecimal;

/**
 * An incoming request to take a payment.
 *
 * @param reference caller-supplied identifier, echoed back in the response
 * @param amount    amount to charge
 */
public record PaymentRequest(String reference, BigDecimal amount) {
}
