package com.example.load_balancer.strategy;

import org.springframework.stereotype.Component;

@Component
public class StrategyManager {
    private final RoundRobinStrategy rr;
    private final LeastConnectionsStrategy lc;

    private RoutingStrategy current;
    public StrategyManager(RoundRobinStrategy rr, LeastConnectionsStrategy lc){
        this.rr = rr;
        this.lc = lc;
        this.current = rr; // default
    }

    public RoutingStrategy getCurrent(){
        return current;
    }

    public void set(String name){
        if("least-connections".equalsIgnoreCase(name))
            current = lc;
        else
            current = rr;
    }
}
