package com.example.load_balancer.metrics;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.example.load_balancer.model.BackendMetrics;
import com.example.load_balancer.model.BackendNode;
import com.example.load_balancer.registry.BackendRegistry;

@Component
public class MetricsPoller {
    private final BackendRegistry registry;
    private final RestTemplate rest;

    public MetricsPoller(BackendRegistry registry, RestTemplate rest){
        this.registry = registry;
        this.rest = rest;
    }

    @Scheduled(fixedRate = 1000)
    public void poll(){
        for (BackendNode backend : registry.getAll()){
            try{
                BackendMetrics metrics = rest.getForObject(backend.url()+"/metrics", BackendMetrics.class);
                if(metrics != null){
                    backend.setActiveConnections(metrics.getActiveConnections());
                    backend.setAvgLatency(metrics.getAvgLatency());
                    backend.setLastLatency(metrics.getLastLatency());
                    backend.setRequestRate(metrics.getRequestRate());
                }
            } catch (Exception e){
                //ignored
            }
        }
    }
}
