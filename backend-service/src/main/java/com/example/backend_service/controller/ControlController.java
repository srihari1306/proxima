package com.example.backend_service.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.backend_service.model.FailureMode;
import com.example.backend_service.simulation.LatencySimulator;
import com.example.backend_service.model.BackendConfig;

@RestController
@RequestMapping("/control")
public class ControlController {
    private final LatencySimulator latencySimulator;
    public ControlController(LatencySimulator latencySimulator) {
        this.latencySimulator = latencySimulator;
    }

    @PostMapping("/config")
    public void configure(@RequestBody BackendConfig config){
        latencySimulator.configure(config.getBaseLatency(), config.getJitter());
    }

    @PostMapping("/degrade")
    public void degrade(@RequestParam int target, @RequestParam int duration){
        latencySimulator.startDegradation(target, duration);
    }

    @PostMapping("/failure")
    public void failure(@RequestParam FailureMode mode){
        latencySimulator.setFailureMode(mode);
    }

    @PostMapping("/recover")
    public void recover(){
        latencySimulator.reset();
    }

}
