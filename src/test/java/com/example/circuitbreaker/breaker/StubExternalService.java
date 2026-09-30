package com.example.circuitbreaker.breaker;

/**
 * Hand-rolled test double: the next result can be scripted, and the number of
 * times it was actually called is recorded so tests can prove the breaker did
 * (or did not) reach the external service.
 */
public final class StubExternalService implements ExternalService {

    private boolean nextResult = true;
    private int invocationCount = 0;

    public void willSucceed() {
        nextResult = true;
    }

    public void willFail() {
        nextResult = false;
    }

    @Override
    public boolean call() {
        invocationCount++;
        return nextResult;
    }

    public int invocationCount() {
        return invocationCount;
    }
}
