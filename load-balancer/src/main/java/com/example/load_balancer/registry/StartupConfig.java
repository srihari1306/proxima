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

        register("backend-1", 8081);
        register("backend-2", 8082);
        register("backend-3", 8083);
    }

    private void register(String id, int port) {
        BackendNode backend = new BackendNode();
        backend.setId(id);
        backend.setHost("localhost");
        backend.setPort(port);
        registry.register(backend);
    }
}