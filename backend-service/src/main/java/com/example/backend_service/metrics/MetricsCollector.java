package com.example.backend_service.metrics;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class MetricsCollector {
    private static final int WINDOW = 100;

    private final Queue<Long> latencies = new ConcurrentLinkedQueue<>();
    private final AtomicLong lastLatency = new AtomicLong(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger count = new AtomicInteger(0);

    public void record(long latency){
        latencies.add(latency);
        lastLatency.set(latency);
        count.incrementAndGet();
        while(count.get() > WINDOW){
            Long removed = latencies.poll();
            if (removed != null) {
                count.decrementAndGet();
            }
        }
    }

    public void inConnections(){
        activeConnections.incrementAndGet();
    }

    public void outConnections(){
        activeConnections.decrementAndGet();
    }

    public double avgLatency(){
        return latencies.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    public long lastLatency(){
        return lastLatency.get();
    }

    public int activeConnections(){
        return activeConnections.get();
    }

    public int requestRate(){
        return count.get();
    }
}
