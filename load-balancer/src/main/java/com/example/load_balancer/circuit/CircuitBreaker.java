package com.example.load_balancer.circuit;

import java.time.Duration;
import java.time.Instant;

public class CircuitBreaker {
    private CircuitState state = CircuitState.CLOSED;
    private int failureCount = 0;
    private Instant openedAt;

    private static final int FAILURE_THRESHOLD =5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    public synchronized boolean allowRequest(){
        if(state == CircuitState.CLOSED){
            return true;
        }
        if (state == CircuitState.OPEN){
            if(Duration.between(openedAt, Instant.now()).compareTo(OPEN_DURATION) > 0){
                state = CircuitState.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    public synchronized void reordSuccess(){
        failureCount = 0;
        state = CircuitState.CLOSED;
    }

    public synchronized void recordFailure(){
        failureCount++;
        if(state == CircuitState.HALF_OPEN){
            open();
            return;
        }
        if(failureCount >= FAILURE_THRESHOLD){
            open();
        }
    }

    private void open(){
        state = CircuitState.OPEN;
        openedAt = Instant.now();
    }

    public CircuitState getState() {
        return state;
    }
}
