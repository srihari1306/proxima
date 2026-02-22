package com.example.load_balancer.logging;

import org.springframework.stereotype.Component;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

@Component
public class RequestLogger {
    private BufferedWriter writer;
    public boolean enabled = false;

    public synchronized void start(String filename) throws IOException {
        stop();
        writer = new BufferedWriter(new FileWriter(filename));
        writer.write("timestamp, strategy, backend_id, active_connections, avg_latency, last_latency, request_rate, observed_latency, success");
        writer.newLine();
        enabled = true;
    }

    public synchronized void log(LogEntry entry){
        if(!enabled) return;
        try{
            writer.write(entry.toCsv());
            writer.newLine();
        } catch (IOException e) {}
    }
    public synchronized void stop() throws IOException {
        if(writer != null){
            writer.close();
            writer = null;
        }
        enabled = false;
    }

    public boolean isEnabled(){
        return enabled;
    }
}
