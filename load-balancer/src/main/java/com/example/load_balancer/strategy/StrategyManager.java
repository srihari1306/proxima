package com.example.load_balancer.strategy;

import org.springframework.stereotype.Component;

@Component
public class StrategyManager {
    private final RoundRobinStrategy rr;
    private final LeastConnectionsStrategy lc;
    private final MLPredictiveStrategy ml;
    private final EWMAPredictiveStrategy ewma;

    private RoutingStrategy current;
    public StrategyManager(RoundRobinStrategy rr, LeastConnectionsStrategy lc, MLPredictiveStrategy ml, EWMAPredictiveStrategy ewma){
        this.rr = rr;
        this.lc = lc;
        this.ml = ml;
        this.ewma = ewma;
        this.current = rr; // default
    }

    public RoutingStrategy getCurrent(){
        return current;
    }

    public void set(String name){
        if("least-connections".equalsIgnoreCase(name))
            current = lc;
        else if("ml-predictive".equalsIgnoreCase(name))
            current = ml;
        else if("ewma-predictive".equalsIgnoreCase(name))
            current = ewma;
        else
            current = rr;
    }
}
