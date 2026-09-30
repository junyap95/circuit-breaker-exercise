package com.example.circuitbreaker.breaker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Behavioural specification for {@link CircuitBreaker}.
 *
 * <p>Tests marked {@code @Disabled} describe behaviour that the starting implementation
 * does not have yet. Remove the annotation as you implement each piece; the goal is a
 * green suite with no {@code @Disabled} left. A few tests near the bottom are left for
 * you to write.
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

    /**
     * These cases are intentionally left for you. Decide on the behaviour, write the
     * test, then make it pass. Each one is a good conversation starter with the interviewer.
     */
    @Nested
    @DisplayName("TODO(candidate): decide and test")
    class LeftForTheCandidate {

        @Test
        @Disabled("TODO(candidate): a failure exactly 30 seconds old - inside or outside the window?")
        void failureExactlyOnTheWindowBoundary() {
            // Hint: the README states the rule this project uses. Encode it here.
        }

        @Test
        @Disabled("TODO(candidate): should a success while CLOSED forget earlier failures?")
        void successfulCallWhileClosedAfterSomeFailures() {
            // Two failures, then a success, then another failure. Is the circuit open or closed?
            // There is more than one defensible answer - pick one and be ready to justify it.
        }

        @Test
        @Disabled("TODO(candidate): what if the external service throws instead of returning false?")
        void externalServiceThrowsAnException() {
            // Should an exception count as a failure? Should it propagate? Should the
            // circuit still be able to open? StubExternalService will need a small extension.
        }
    }
}
