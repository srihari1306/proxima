package com.example.backend_service.controller;

import com.example.backend_service.metrics.MetricsCollector;
import com.example.backend_service.service.WorkerPoolService;
import com.example.backend_service.simulation.LatencySimulator;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProcessContoller {

    private final LatencySimulator latencySimulator;
    private final MetricsCollector metricsCollector;
    private final WorkerPoolService workerPool;

    public ProcessContoller(LatencySimulator latencySimulator, MetricsCollector metricsCollector, WorkerPoolService workerPool) {
        this.latencySimulator = latencySimulator;
        this.metricsCollector = metricsCollector;
        this.workerPool = workerPool;
    }

    @GetMapping("/process")
    public ResponseEntity<String> process(){
        long start = System.currentTimeMillis();
        metricsCollector.inConnections();

        CompletableFuture<Void> future = new CompletableFuture<>();

        try{
            workerPool.submit(()->{
                try{
                    latencySimulator.simulatework();
                    future.complete(null);
                } catch (Exception e){
                    future.completeExceptionally(e);
                }
            });
        } catch (Exception e){
            metricsCollector.outConnections();
            return ResponseEntity.status(503).body("Queue Full");
        }

        try{
            future.get();
        } catch(Exception e){
            metricsCollector.outConnections();
            return ResponseEntity.status(500).body("Processing Error");
        }
        long totalLatency = System.currentTimeMillis() - start;
        metricsCollector.record(totalLatency);
        metricsCollector.outConnections();
        return ResponseEntity.ok("Processed in " + totalLatency + " ms");
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics(){
        return ResponseEntity.ok(new BackendMetics(
            metricsCollector.avgLatency(),
            metricsCollector.lastLatency(),
            metricsCollector.activeConnections(),
            metricsCollector.requestRate(),
            workerPool.getQueueSize(),
            workerPool.getActiveCount()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health(){
        return latencySimulator.isFailed() ? ResponseEntity.status(503).body("DOWN") : ResponseEntity.ok("UP");
    }


    record BackendMetics(double avgLatency, long lastLatency, int activeConnections, double requestRate, int queueSize, int activeWorkers){}
}
