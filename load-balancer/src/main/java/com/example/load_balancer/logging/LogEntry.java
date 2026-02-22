package com.example.load_balancer.logging;

public class LogEntry {
    public long timestamp;
    public String strategy;
    public String backendId;
    public int activeConnections;
    public double avgLatency;
    public long lastLatency;
    public double requestRate;
    public long observedLatency;
    public boolean success;

    public String toCsv(){
        return timestamp + "," + strategy + "," + backendId + "," + activeConnections + "," + avgLatency + "," + lastLatency + "," + requestRate + "," + observedLatency + "," + success;
    }
}
