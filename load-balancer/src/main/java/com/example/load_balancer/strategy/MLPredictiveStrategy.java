package com.example.load_balancer.strategy;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import com.example.load_balancer.ml.BackendMetrics;
import com.example.load_balancer.ml.PredictionRequest;
import com.example.load_balancer.ml.PredictionResponse;
import com.example.load_balancer.model.BackendNode;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
public class MLPredictiveStrategy implements RoutingStrategy{
    private final RestTemplate rest;
    private final LeastConnectionsStrategy fallback;

    @Value("${ml.service-url}")
    private String mlUrl;

    @Value("${ml.timeout-ms}")
    private int timeoutMs;

    @Value("${ml.cache-ttl-ms}")
    private int cacheTtl;

    private long lastPredictionTime = 0;
    private List<String> cachedRanking = null;

    public MLPredictiveStrategy(RestTemplate rest, LeastConnectionsStrategy fallback){
        this.rest = rest;
        this.fallback = fallback;
    }

    // @Override
    // public BackendNode select(List<BackendNode> candidates){
    //     if(candidates.isEmpty()) return null;

    //     long now = System.currentTimeMillis();

    //     if(cachedRanking != null && (now - lastPredictionTime) < cacheTtl){
    //         return pickFromRanking(candidates, cachedRanking);
    //     }
    //     try{
    //         PredictionRequest request = new PredictionRequest();
    //         request.setBackends(candidates.stream().map(b-> new BackendMetrics(
    //             b.getId(),
    //             b.getActiveConnections(),
    //             b.getAvgLatency(),
    //             b.getLastLatency(),
    //             b.getRequestRate()
    //         )).collect(Collectors.toList()));

    //         long start = System.currentTimeMillis();

    //         PredictionResponse response = rest.postForObject(mlUrl+"/predict", request, PredictionResponse.class);
    //         long duration = System.currentTimeMillis() - start;

    //         if(duration > timeoutMs || response == null){
    //             return fallback.select(candidates);
    //         }

    //         cachedRanking = response.getRanked_backends();
    //         lastPredictionTime = now;

    //         return pickFromRanking(candidates, cachedRanking);
    //     }
    //     catch(Exception e){
    //         return fallback.select(candidates);
    //     }
    // }

    @Override
    public BackendNode select(List<BackendNode> candidates) {

        if (candidates.isEmpty()) return null;

        // ====================================================
        // 5% Exploration (not 10% — keep it small)
        // ====================================================
        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            return candidates.get(
                    ThreadLocalRandom.current().nextInt(candidates.size())
            );
        }

        long now = System.currentTimeMillis();

        if (cachedRanking != null &&
            (now - lastPredictionTime) < cacheTtl) {

            return pickSmart(candidates, cachedRanking);
        }

        try {
            PredictionRequest request = new PredictionRequest();
            request.setBackends(
                    candidates.stream()
                            .map(b -> new BackendMetrics(
                                    b.getId(),
                                    b.getActiveConnections(),
                                    b.getAvgLatency(),
                                    b.getLastLatency(),
                                    b.getRequestRate()
                            ))
                            .collect(Collectors.toList())
            );

            PredictionResponse response =
                    rest.postForObject(mlUrl + "/predict",
                            request,
                            PredictionResponse.class);

            if (response == null) {
                return fallback.select(candidates);
            }

            cachedRanking = response.getRanked_backends();
            lastPredictionTime = now;

            return pickSmart(candidates, cachedRanking);

        } catch (Exception e) {
            return fallback.select(candidates);
        }
    }

    private BackendNode pickSmart(List<BackendNode> candidates,
                                List<String> ranking) {

        // Take top 3 ML-ranked candidates
        List<BackendNode> top = new ArrayList<>();

        for (String id : ranking) {
            for (BackendNode b : candidates) {
                if (b.getId().equals(id)) {
                    top.add(b);
                    break;
                }
            }
            if (top.size() == 3) break;
        }

        if (top.isEmpty()) {
            return fallback.select(candidates);
        }

        // Power of two choices inside top 3
        BackendNode a = top.get(
                ThreadLocalRandom.current().nextInt(top.size())
        );
        BackendNode b = top.get(
                ThreadLocalRandom.current().nextInt(top.size())
        );

        return a.getActiveConnections() <= b.getActiveConnections() ? a : b;
    }

    private BackendNode pickFromRanking(List<BackendNode> candidates, List<String> ranking){
        // for(String id: ranking){
        //     for(BackendNode b: candidates){
        //         if(b.getId().equals(id))
        //             return b;
        //     }
        // }

        // return candidates.get(0);
        List<BackendNode> top = new ArrayList<>();
        for(String id: ranking){
            for(BackendNode b: candidates){
                if(b.getId().equals(id)){
                    top.add(b);
                    break;
                }
            }
            if (top.size() == 2) break;
        }
        if(top.isEmpty()) return candidates.get(0);
        
        return top.stream().min(Comparator.comparingInt(BackendNode::getActiveConnections)).orElse(top.get(0));
    }


    @Override
    public String name() {
        return "ml-predictive";
    }
}
