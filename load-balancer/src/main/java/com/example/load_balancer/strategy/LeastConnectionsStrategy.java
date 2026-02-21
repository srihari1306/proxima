package com.example.load_balancer.strategy;

import com.example.load_balancer.model.BackendNode;
import java.util.List;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class LeastConnectionsStrategy implements RoutingStrategy{
    @Override
    public BackendNode select(List<BackendNode> candidates){
        if(candidates.isEmpty()) return null;
        return candidates.stream().min(Comparator.comparingInt(BackendNode::getActiveConnections)).orElse(null);
    }

    @Override
    public String name(){
        return "least-connections";
    }
}
