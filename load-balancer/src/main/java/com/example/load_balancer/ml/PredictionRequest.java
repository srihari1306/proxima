package com.example.load_balancer.ml;

import java.util.List;


public class PredictionRequest {
    private List<BackendMetrics> backends;
    public List<BackendMetrics> getBackends(){
        return backends;
    }

    public void setBackends(List<BackendMetrics> backends){
        this.backends = backends;
    }
}
