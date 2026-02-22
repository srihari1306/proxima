package com.example.load_balancer.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.load_balancer.logging.RequestLogger;
import com.example.load_balancer.strategy.StrategyManager;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final StrategyManager manager;
    private final RequestLogger logger;

    public AdminController(StrategyManager manager, RequestLogger logger){
        this.manager = manager;
        this.logger = logger;
    }

    @PostMapping("/strategy")
    public String set(@RequestParam String name){
        manager.set(name);
        return "strategy=" +name;
    }

    @PostMapping("/start-logging")
    public String startLogging(@RequestParam String file) throws Exception {
        logger.start(file);
        return "logging started: " + file;
    }

    @PostMapping("/stop-logging")
    public String stopLogging() throws Exception {
        logger.stop();
        return "logging stopped";
    } 
}
