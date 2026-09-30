package com.example.circuitbreaker.breaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Behavioural specification for {@link CircuitBreaker}.
 *
 * <p>Configuration used throughout: 3 failures within 30 seconds opens the circuit;
 * the circuit stays open for 10 seconds. Time starts at 12:00:00 and only moves when
 * the test advances the clock.
 */
class CircuitBreakerTest {

    private static final Instant START = Instant.parse("2024-01-01T12:00:00Z");
    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration FAILURE_WINDOW = Duration.ofSeconds(30);
    private static final Duration OPEN_DURATION = Duration.ofSeconds(10);

    private StubExternalService externalService;
    private MutableClock clock;
    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        externalService = new StubExternalService();
        clock = new MutableClock(START);
        breaker = new CircuitBreaker(externalService, clock,
                new CircuitBreakerProperties(FAILURE_THRESHOLD, FAILURE_WINDOW, OPEN_DURATION));
    }

    @Nested
    @DisplayName("while CLOSED")
    class WhileClosed {

        @Test
        void startsClosed() {
            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        }

        @Test
        void successfulCallReachesTheServiceAndReportsSuccess() {
            externalService.willSucceed();

            CallResult result = breaker.call();

            assertThat(result).isEqualTo(CallResult.SUCCESS);
            assertThat(externalService.invocationCount()).isEqualTo(1);
            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        }

        @Test
        void failedCallStillReachesTheServiceAndReportsFailure() {
            externalService.willFail();

            CallResult result = breaker.call();

            assertThat(result).isEqualTo(CallResult.FAILURE);
            assertThat(externalService.invocationCount()).isEqualTo(1);
        }

        @Test
        void fewerFailuresThanTheThresholdKeepsTheCircuitClosed() {
            externalService.willFail();

            breaker.call();
            clock.advanceSeconds(1);
            breaker.call();

            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        }
    }

    @Nested
    @DisplayName("opening the circuit")
    class OpeningTheCircuit {

        @Test
        void threeFailuresWithinTheWindowOpenTheCircuit() {
            // 12:00:00, 12:00:10, 12:00:20 - all inside one 30-second window
            externalService.willFail();
            breaker.call();
            clock.advanceSeconds(10);
            breaker.call();
            clock.advanceSeconds(10);
            breaker.call();

            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        }

        @Test
        void failuresOlderThanTheWindowAreNotCounted() {
            // 12:00:00, 12:00:20, 12:00:31 - by the third failure the first one has expired
            externalService.willFail();
            breaker.call();
            clock.advanceSeconds(20);
            breaker.call();
            clock.advanceSeconds(11);
            breaker.call();

            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        }

        @Test
        void aFourthFailureCanOpenTheCircuitEvenIfTheFirstOneExpired() {
            // 12:00:00 expires; 12:00:20, 12:00:31, 12:00:35 are three failures inside a window
            externalService.willFail();
            breaker.call();
            clock.advanceSeconds(20);
            breaker.call();
            clock.advanceSeconds(11);
            breaker.call();
            clock.advanceSeconds(4);
            breaker.call();

            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        }
    }

    @Nested
    @DisplayName("while OPEN")
    class WhileOpen {

        @BeforeEach
        void openTheCircuit() {
            externalService.willFail();
            breaker.call();
            breaker.call();
            breaker.call();
            assertThat(breaker.getState())
                    .as("precondition: three failures should have opened the circuit")
                    .isEqualTo(CircuitBreakerState.OPEN);
        }

        @Test
        void callsAreRejectedWithoutReachingTheService() {
            int callsBefore = externalService.invocationCount();
            clock.advanceSeconds(5);

            CallResult result = breaker.call();

            assertThat(result).isEqualTo(CallResult.REJECTED);
            assertThat(externalService.invocationCount()).isEqualTo(callsBefore);
            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        }

        @Test
        void afterTheCooldownATrialCallReachesTheService() {
            int callsBefore = externalService.invocationCount();
            clock.advance(OPEN_DURATION);

            breaker.call();

            assertThat(externalService.invocationCount()).isEqualTo(callsBefore + 1);
        }
    }

    @Nested
    @DisplayName("trial call after the cooldown")
    class TrialCall {

        @BeforeEach
        void openTheCircuitAndWaitForTheCooldown() {
            externalService.willFail();
            breaker.call();
            breaker.call();
            breaker.call();
            assertThat(breaker.getState())
                    .as("precondition: three failures should have opened the circuit")
                    .isEqualTo(CircuitBreakerState.OPEN);
            clock.advance(OPEN_DURATION);
        }

        @Test
        void successfulTrialClosesTheCircuit() {
            externalService.willSucceed();

            CallResult result = breaker.call();

            assertThat(result).isEqualTo(CallResult.SUCCESS);
            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        }

        @Test
        void successfulTrialClearsTheFailureHistory() {
            externalService.willSucceed();
            breaker.call();

            // Two fresh failures: if the three old ones were still being counted the
            // circuit would open again here, but a recovered circuit starts from a clean slate.
            externalService.willFail();
            clock.advanceSeconds(1);
            breaker.call();
            clock.advanceSeconds(1);
            breaker.call();

            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        }

        @Test
        void failedTrialKeepsTheCircuitOpen() {
            externalService.willFail();

            CallResult result = breaker.call();

            assertThat(result).isEqualTo(CallResult.FAILURE);
            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        }

        @Test
        void failedTrialStartsAFreshCooldown() {
            externalService.willFail();
            breaker.call(); // failed trial at 12:00:10
            int callsBefore = externalService.invocationCount();

            clock.advanceSeconds(9); // 12:00:19 - still inside the new cooldown
            assertThat(breaker.call()).isEqualTo(CallResult.REJECTED);
            assertThat(externalService.invocationCount()).isEqualTo(callsBefore);

            clock.advanceSeconds(1); // 12:00:20 - new cooldown has elapsed
            breaker.call();
            assertThat(externalService.invocationCount()).isEqualTo(callsBefore + 1);
        }
    }

    @Nested
    @DisplayName("window boundary")
    class WindowBoundary {

        @Test
        void failureExactlyThirtySecondsOldHasExpired() {
            // Rule: expired when failureTime <= now - 30s. 12:00:00 is exactly 30s old at 12:00:30.
            externalService.willFail();
            breaker.call();
            clock.advanceSeconds(15);
            breaker.call();
            clock.advanceSeconds(15);
            breaker.call();

            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        }

        @Test
        void failureJustInsideThirtySecondsIsStillCounted() {
            // 12:00:00, 12:00:15, 12:00:29 - the first failure is 29s old and still counts.
            externalService.willFail();
            breaker.call();
            clock.advanceSeconds(15);
            breaker.call();
            clock.advanceSeconds(14);
            breaker.call();

            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        }
    }

    @Nested
    @DisplayName("successes while CLOSED")
    class SuccessesWhileClosed {

        /**
         * Decision: a success does not wipe the failure history. The rule is "3 failures within
         * 30 seconds", not "3 consecutive failures", so a dependency that flaps
         * (fail, ok, fail, ok, fail) still trips the breaker.
         */
        @Test
        void successDoesNotResetTheFailureHistory() {
            externalService.willFail();
            breaker.call();
            clock.advanceSeconds(1);
            breaker.call();

            externalService.willSucceed();
            clock.advanceSeconds(1);
            assertThat(breaker.call()).isEqualTo(CallResult.SUCCESS);
            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);

            externalService.willFail();
            clock.advanceSeconds(1);
            breaker.call();

            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        }
    }

    @Nested
    @DisplayName("external service throws")
    class ExternalServiceThrows {

        /**
         * Decision: an exception is a failure as far as the breaker is concerned, but the
         * breaker does not swallow it. A dependency that times out must still be able to
         * trip the circuit, and the caller must still find out that something went wrong.
         */
        @Test
        void exceptionPropagatesAndCountsAsAFailure() {
            externalService.willThrow(new IllegalStateException("bank timed out"));

            for (int i = 0; i < FAILURE_THRESHOLD; i++) {
                assertThatThrownBy(breaker::call)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("bank timed out");
                clock.advanceSeconds(1);
            }

            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
            assertThat(externalService.invocationCount()).isEqualTo(FAILURE_THRESHOLD);
        }

        @Test
        void exceptionDuringTheTrialReopensTheCircuit() {
            externalService.willFail();
            breaker.call();
            breaker.call();
            breaker.call();
            clock.advance(OPEN_DURATION);

            externalService.willThrow(new IllegalStateException("still down"));
            assertThatThrownBy(breaker::call).isInstanceOf(IllegalStateException.class);

            assertThat(breaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
            clock.advanceSeconds(1);
            assertThat(breaker.call()).isEqualTo(CallResult.REJECTED);
        }
    }
}
