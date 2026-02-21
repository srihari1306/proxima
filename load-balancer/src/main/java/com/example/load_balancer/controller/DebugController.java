package com.example.load_balancer.controller;

import org.springframework.web.bind.annotation.RestController;
import com.example.load_balancer.registry.BackendRegistry;
import com.example.load_balancer.strategy.LeastConnectionsStrategy;
import com.example.load_balancer.strategy.RoundRobinStrategy;
import com.example.load_balancer.model.BackendNode;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;

@RestController
public class DebugController {
    private final BackendRegistry registry;
    private final RoundRobinStrategy rr;
    private final LeastConnectionsStrategy lc;

    public DebugController(BackendRegistry registry, RoundRobinStrategy rr, LeastConnectionsStrategy lc){
        this.registry = registry;
        this.rr = rr;
        this.lc = lc;
    }

    @GetMapping("/debug/backends")
    public List<BackendNode> getBackends(){
        return new ArrayList<>(registry.getAll());
    }

    @GetMapping("/debug/rr")
    public String testRR(){
        List<BackendNode> list = new ArrayList<>(registry.getAll());
        BackendNode b = rr.select(list);
        return b == null ? "No backend" : b.getId();
    }

    @GetMapping("/debug/lc")
    public String testLC(){
        List<BackendNode> list = new ArrayList<>(registry.getAll());
        BackendNode b = lc.select(list);
        return b == null ? "No backend" : b.getId();
    }

}
