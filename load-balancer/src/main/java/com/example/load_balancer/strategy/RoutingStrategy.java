package com.example.load_balancer.strategy;

import com.example.load_balancer.model.BackendNode;
import java.util.List;

public interface RoutingStrategy {
    BackendNode select(List<BackendNode> candidates);

    String name();

    default void before(BackendNode node) {}
    default void after(BackendNode backend, long latency, boolean success) {}
}
