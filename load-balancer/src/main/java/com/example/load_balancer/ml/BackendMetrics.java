package com.example.load_balancer.ml;

import lombok.Data;

@Data
public class BackendMetrics {
    private String backend_id;
    private int active_connections;
    private double avg_latency;
    private long last_latency;
    private double request_rate;

    public BackendMetrics() {}

    public BackendMetrics(String id, int ac, double avg, long last, double rate) {
        this.backend_id = id;
        this.active_connections = ac;
        this.avg_latency = avg;
        this.last_latency = last;
        this.request_rate = rate;
    }

    public String getBackend_id() { return backend_id; }
}
