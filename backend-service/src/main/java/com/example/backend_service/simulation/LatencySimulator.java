package com.example.backend_service.simulation;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.example.backend_service.model.FailureMode;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

@Component
public class LatencySimulator {
    private final AtomicInteger baseLatencyMs = new AtomicInteger(50);
    private final AtomicInteger jitterMs = new AtomicInteger(10);
    private final AtomicReference<FailureMode> failureMode = new AtomicReference<>(FailureMode.NONE);

    private volatile boolean degrading = false;
    private long degradionStart;
    private int targetLatency;
    private int durationSeconds;

    public void simulatework() throws InterruptedException {
        if(failureMode.get() == FailureMode.RETURN_ERROR){
            throw new RuntimeException("Simulated backenderror");
        }

        int latency = calculateLatency();
        if(failureMode.get() == FailureMode.TIMEOUT){
            latency = 30_000;
        }
        Thread.sleep(latency);
    }

    private int calculateLatency(){
        int base = baseLatencyMs.get();
        int jitter = ThreadLocalRandom.current().nextInt(-jitterMs.get(), jitterMs.get() + 1);
        if(degrading){
            long elapsed = System.currentTimeMillis() - degradionStart;
            long total = durationSeconds * 1000L;

            if(elapsed >=total){
                base = targetLatency;
            }
            else{
                double factor = (double) elapsed / total;
                base = (int) (base + factor * (targetLatency - base));
            }
        }
        return Math.max(0, base + jitter);
    }

    public void configure(int baseLatency, int jitter){
        this.baseLatencyMs.set(baseLatency);
        this.jitterMs.set(jitter);
    }

    public void startDegradation(int target, int seconds){
        this.degrading=true;
        this.degradionStart = System.currentTimeMillis();
        this.targetLatency = target;
        this.durationSeconds = seconds;
    }

    public void setFailureMode(FailureMode mode){
        this.failureMode.set(mode);
    }

    public void reset(){
        this.degrading = false;
        this.failureMode.set(FailureMode.NONE);
    }

    public boolean isFailed(){
        return failureMode.get() != FailureMode.NONE;
    }

}
