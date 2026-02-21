package com.example.load_balancer.registry;

import com.example.load_balancer.model.BackendNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;


@Component
public class BackendRegistry {
    private final Map<String, BackendNode> backends = new ConcurrentHashMap<>();

    public void register(BackendNode backend){
        backends.put(backend.getId(), backend);
    }

    public Collection<BackendNode> getAll(){
        return backends.values();
    }

    public BackendNode get(String id){
        return backends.get(id);
    }   

}
