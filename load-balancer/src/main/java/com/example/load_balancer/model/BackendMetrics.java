package com.example.load_balancer.model;

import lombok.Data;

@Data
public class BackendMetrics {
    private int activeConnections;
    private double avgLatency;
    private long lastLatency;
    private double requestRate;
}
