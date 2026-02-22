package com.example.load_balancer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.example.load_balancer.registry.BackendRegistry;
import com.example.load_balancer.strategy.RoutingStrategy;
import com.example.load_balancer.strategy.StrategyManager;
import com.example.load_balancer.circuit.CircuitBreakerManager;
import com.example.load_balancer.logging.LogEntry;
import com.example.load_balancer.logging.RequestLogger;
import com.example.load_balancer.model.BackendNode;
import com.example.load_balancer.model.HealthStatus;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import java.util.stream.Collectors;


@RestController
public class LoadBalancerContoller {
    private final BackendRegistry registry;
    private final StrategyManager manager;
    private final CircuitBreakerManager circuitManager;
    private final RequestLogger logger;
    private RestTemplate rest;

    public LoadBalancerContoller(BackendRegistry registry, StrategyManager manager, RestTemplate rest, CircuitBreakerManager circuitManager, RequestLogger logger){
        this.registry = registry;
        this.manager = manager;
        this.circuitManager = circuitManager;
        this.logger = logger;
        this.rest = rest;
    }

    @GetMapping("/api/process")
    public ResponseEntity<?> route(){

        List<BackendNode> healthy = registry.getAll().stream()
            .filter(b->b.getHealthStatus() == HealthStatus.HEALTHY)
            .filter(b -> circuitManager.allow(b.getId()))
            .collect(Collectors.toList());


        if(healthy.isEmpty()){
            return ResponseEntity.status(503).body("No healthy backend");
        }

        RoutingStrategy strategy = manager.getCurrent();
        BackendNode backend = strategy.select(healthy);

        if(backend == null){
            return ResponseEntity.status(503).body("No backend selected");
        }

        long start = System.currentTimeMillis();
        LogEntry entry = new LogEntry();
        entry.timestamp = System.currentTimeMillis();
        entry.strategy = strategy.name();
        entry.backendId = backend.getId();
        entry.activeConnections = backend.getActiveConnections();
        entry.avgLatency = backend.getAvgLatency();
        entry.requestRate = backend.getRequestRate();
        entry.lastLatency = backend.getLastLatency();
        try{
            ResponseEntity<String> response = rest.getForEntity(backend.url()+"/process", String.class);

            long latency = System.currentTimeMillis() - start;

            circuitManager.success(backend.getId());
            strategy.after(backend, latency, true);
            entry.observedLatency = latency;
            entry.success = true;
            logger.log(entry);

            return response;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;

            circuitManager.failure(backend.getId());
            strategy.after(backend, latency, false);
            entry.observedLatency = latency;
            entry.success = false;
            logger.log(entry);

            return ResponseEntity.status(500).body("backend failure");
        }
    }
}
