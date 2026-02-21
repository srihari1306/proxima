package com.example.load_balancer.strategy;
import com.example.load_balancer.model.BackendNode;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;


@Component
public class RoundRobinStrategy implements RoutingStrategy {
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public BackendNode select(List<BackendNode> candidates){
        if(candidates.isEmpty()) return null;
        int i = Math.abs(index.getAndIncrement() % candidates.size());
        return candidates.get(i);
    }

    @Override
    public String name() {
        return "round-robin";
    }

}
