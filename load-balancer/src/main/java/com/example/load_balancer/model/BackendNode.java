package com.example.load_balancer.model;
import java.time.Instant;


public class BackendNode {
    private String id;
    private String host;
    private int port;

    private int activeConnections;
    private double avgLatency;
    private long lastLatency;
    private double requestRate;

    private HealthStatus healthStatus = HealthStatus.HEALTHY;
    private int consecutiveFailures = 0;
    private Instant lastHealthCheck;

    public String url() {
        return "http://" + host + ":" + port;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public int getActiveConnections() {
        return activeConnections;
    }
    public void setActiveConnections(int activeConnections) {
        this.activeConnections = activeConnections;
    }
    public double getAvgLatency() {
        return avgLatency;
    }
    public void setAvgLatency(double avgLatency) {
        this.avgLatency = avgLatency;
    }
    public long getLastLatency() {
        return lastLatency;
    }
    public void setLastLatency(long lastLatency) {
        this.lastLatency = lastLatency;
    }
    public double getRequestRate() {
        return requestRate;
    }
    public void setRequestRate(double requestRate) {
        this.requestRate = requestRate;
    }
    public HealthStatus getHealthStatus() {
        return healthStatus;
    }
    public void setHealthStatus(HealthStatus healthStatus) {
        this.healthStatus = healthStatus;
    }
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }
    public Instant getLastHealthCheck() {
        return lastHealthCheck;
    }
    public void setLastHealthCheck(Instant lastHealthCheck) {
        this.lastHealthCheck = lastHealthCheck;
    }
    
}
