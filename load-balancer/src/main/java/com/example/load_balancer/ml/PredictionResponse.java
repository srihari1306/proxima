package com.example.load_balancer.ml;

import java.util.List;

public class PredictionResponse {
    private List<String> ranked_backends;
    public List<String> getRanked_backends(){
        return ranked_backends;
    }
    public void setRanked_backends(List<String> ranked_backends){
        this.ranked_backends = ranked_backends;
    }
}
