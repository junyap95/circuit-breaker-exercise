package com.example.circuitbreaker.payment;

public enum PaymentStatus {

    /** The bank accepted the payment. */
    APPROVED,

    /** The bank was contacted but did not accept the payment. */
    DECLINED,

    /** The bank was not contacted because the circuit breaker is open. Safe to retry later. */
    UNAVAILABLE
}
