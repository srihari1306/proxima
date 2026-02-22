package com.example.load_balancer.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.load_balancer.strategy.StrategyManager;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final StrategyManager manager;

    public AdminController(StrategyManager manager){
        this.manager = manager;
    }

    @PostMapping("/strategy")
    public String set(@RequestParam String name){
        manager.set(name);
        return "strategy=" +name;
    }
}
