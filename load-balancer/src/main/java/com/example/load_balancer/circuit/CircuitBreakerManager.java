package com.example.load_balancer.circuit;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CircuitBreakerManager {
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();


    public boolean allow(String backendId){
        return breakers.computeIfAbsent(backendId, id->new CircuitBreaker()).allowRequest();
    }

    public void success(String backendId){
        breakers.get(backendId).reordSuccess();
    }
    public void failure(String backendId){
        breakers.get(backendId).recordFailure();
    }

    public CircuitState getState(String backendId){
        return breakers.get(backendId).getState();
    }
}
