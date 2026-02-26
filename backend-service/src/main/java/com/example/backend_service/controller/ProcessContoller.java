package com.example.backend_service.controller;

import com.example.backend_service.metrics.MetricsCollector;
import com.example.backend_service.simulation.LatencySimulator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProcessContoller {

    private final LatencySimulator latencySimulator;
    private final MetricsCollector metricsCollector;

    public ProcessContoller(LatencySimulator latencySimulator, MetricsCollector metricsCollector) {
        this.latencySimulator = latencySimulator;
        this.metricsCollector = metricsCollector;
    }

    @GetMapping("/process")
    public ResponseEntity<String> process(){
        long start = System.currentTimeMillis();
        metricsCollector.inConnections();

        try{
            latencySimulator.simulatework();
            long latency = System.currentTimeMillis() - start;
            metricsCollector.record(latency);
            return ResponseEntity.ok("OK");
        } catch (Exception e){
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
        finally {
            metricsCollector.outConnections();
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics(){
        return ResponseEntity.ok(new BackendMetics(
            metricsCollector.avgLatency(),
            metricsCollector.lastLatency(),
            metricsCollector.activeConnections(),
            metricsCollector.requestRate()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health(){
        return latencySimulator.isFailed() ? ResponseEntity.status(503).body("DOWN") : ResponseEntity.ok("UP");
    }


    record BackendMetics(double avgLatency, long lastLatency, int activeConnections, double requestRate){}
}
