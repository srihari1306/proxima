package com.example.load_balancer.registry;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Configuration;
import com.example.load_balancer.model.BackendNode;

@Configuration
public class StartupConfig implements ApplicationRunner {

    private final BackendRegistry registry;

    public StartupConfig(BackendRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {

        BackendNode backend = new BackendNode();
        backend.setId("backend-1");
        backend.setHost("localhost");
        backend.setPort(8081);

        registry.register(backend);

        System.out.println("Registered backend-1");
    }
}