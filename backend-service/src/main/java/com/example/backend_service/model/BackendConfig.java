package com.example.backend_service.model;

import lombok.Data;

@Data
public class BackendConfig {
    private int baseLatency;
    private int jitter;

}
