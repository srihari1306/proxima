package com.example.load_balancer.health;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.example.load_balancer.model.BackendNode;
import com.example.load_balancer.model.HealthStatus;
import com.example.load_balancer.registry.BackendRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;


@Component
public class HealthChecker {
    private final BackendRegistry registry;
    private final RestTemplate restTemplate = new RestTemplate();

    public HealthChecker(BackendRegistry registry){
        this.registry = registry;
    }

    @Scheduled(fixedRate = 5000)//every 5 secs
    public void check(){
        for(BackendNode backend: registry.getAll()){
            try{
                restTemplate.getForEntity(backend.url()+"/health",String.class);
                backend.setHealthStatus(HealthStatus.HEALTHY);
                backend.setConsecutiveFailures(0);
            } catch (Exception e) {
                int failures = backend.getConsecutiveFailures() + 1;
                backend.setConsecutiveFailures(failures);

                backend.setHealthStatus(failures >= 3 ? HealthStatus.UNHEALTHY : HealthStatus.DEGRADED);
            }

            backend.setLastHealthCheck(Instant.now());
        }
    }


}
