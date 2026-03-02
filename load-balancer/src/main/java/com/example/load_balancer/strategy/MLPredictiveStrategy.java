package com.example.load_balancer.strategy;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import com.example.load_balancer.ml.BackendMetrics;
import com.example.load_balancer.ml.PredictionRequest;
import com.example.load_balancer.ml.PredictionResponse;
import com.example.load_balancer.model.BackendNode;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * ML-based predictive routing strategy.
 * 
 * Uses external ML service for backend ranking with:
 * - 10% exploration to prevent starvation
 * - Top-K selection for load balancing
 * - Power of Two Choices within top-K
 * - 200ms prediction caching
 * - Automatic fallback to Least Connections
 * 
 * Note: This is intentionally a distributed architecture (ML as external service)
 * to reflect production reality. The architectural overhead is part of the evaluation.
 */
@Component
public class MLPredictiveStrategy implements RoutingStrategy {
    
    private final LeastConnectionsStrategy fallback;

    @Value("${ml.service-url:http://localhost:8000}")
    private String mlUrl;

    @Value("${ml.timeout-ms:50}")
    private int timeoutMs;

    @Value("${ml.cache-ttl-ms:200}")
    private int cacheTtl;

    @Value("${ml.exploration-rate:0.10}")
    private double explorationRate;

    @Value("${ml.top-k:3}")
    private int topK;

    // RestTemplate initialized after @Value injection
    private RestTemplate restTemplate;

    // Cache for ML predictions (volatile for thread visibility)
    private volatile PredictionCache cache;

    public MLPredictiveStrategy(LeastConnectionsStrategy fallback) {
        this.fallback = fallback;
    }

    /**
     * Initialize RestTemplate with timeout configuration.
     * Called after @Value fields are injected.
     */
    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public BackendNode select(List<BackendNode> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        // Single backend - no choice needed
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // ============================================
        // EXPLORATION: Random selection (10%)
        // ============================================
        if (shouldExplore()) {
            return randomSelection(candidates);
        }

        // ============================================
        // EXPLOITATION: ML-guided selection (90%)
        // ============================================
        try {
            List<String> ranking = getRanking(candidates);
            return selectFromTopK(candidates, ranking);
            
        } catch (Exception e) {
            // ML service failed - fallback to Least Connections
            return fallback.select(candidates);
        }
    }

    /**
     * Decide whether to explore (random) or exploit (ML-guided).
     * Uses epsilon-greedy strategy.
     */
    private boolean shouldExplore() {
        return ThreadLocalRandom.current().nextDouble() < explorationRate;
    }

    /**
     * Random backend selection for exploration.
     * Prevents backend starvation and enables recovery detection.
     */
    private BackendNode randomSelection(List<BackendNode> candidates) {
        int randomIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(randomIndex);
    }

    /**
     * Get ML ranking with caching.
     * Cache TTL prevents excessive ML service calls.
     */
    private List<String> getRanking(List<BackendNode> candidates) {
        long now = System.currentTimeMillis();
        
        // Check cache validity
        if (cache != null && (now - cache.timestamp) < cacheTtl) {
            return cache.ranking;
        }

        // Call ML service for fresh ranking
        List<String> ranking = callMLService(candidates);
        
        // Update cache (volatile write ensures visibility)
        cache = new PredictionCache(ranking, now);
        
        return ranking;
    }

    /**
     * Call ML inference service via REST.
     * Throws exception on timeout or invalid response.
     */
    private List<String> callMLService(List<BackendNode> candidates) {
        // Build prediction request
        PredictionRequest request = new PredictionRequest();
        request.setBackends(
            candidates.stream()
                .map(this::toBackendMetrics)
                .collect(Collectors.toList())
        );

        // Call ML service with configured timeout
        PredictionResponse response = restTemplate.postForObject(
            mlUrl + "/predict",
            request,
            PredictionResponse.class
        );

        // Validate response
        if (response == null || response.getRanked_backends() == null) {
            throw new RuntimeException("ML service returned null response");
        }

        return response.getRanked_backends();
    }

    /**
     * Convert BackendNode to BackendMetrics for ML service.
     */
    private BackendMetrics toBackendMetrics(BackendNode node) {
        return new BackendMetrics(
            node.getId(),
            node.getActiveConnections(),
            node.getAvgLatency(),
            node.getLastLatency(),
            node.getRequestRate()
        );
    }

    /**
     * Select backend from top-K ML-ranked candidates.
     * 
     * Uses Power of Two Choices within top-K for:
     * - Load balancing (avoid overloading single backend)
     * - Graceful degradation (if ML ranking imperfect)
     * - Better distribution than pure greedy selection
     */
    private BackendNode selectFromTopK(List<BackendNode> allCandidates, 
                                       List<String> ranking) {
        // Extract top-K candidates from ML ranking
        List<BackendNode> topCandidates = new ArrayList<>();
        
        for (String rankedId : ranking) {
            if (topCandidates.size() >= topK) {
                break;
            }
            
            // Find this backend in available candidates
            for (BackendNode candidate : allCandidates) {
                if (candidate.getId().equals(rankedId)) {
                    topCandidates.add(candidate);
                    break;
                }
            }
        }

        // Safety: if no top candidates found, use all available
        if (topCandidates.isEmpty()) {
            topCandidates = allCandidates;
        }

        // Single candidate - return it
        if (topCandidates.size() == 1) {
            return topCandidates.get(0);
        }

        // Power of Two Choices: randomly sample 2, pick less loaded
        BackendNode choice1 = topCandidates.get(
            ThreadLocalRandom.current().nextInt(topCandidates.size())
        );
        BackendNode choice2 = topCandidates.get(
            ThreadLocalRandom.current().nextInt(topCandidates.size())
        );

        return choice1.getActiveConnections() <= choice2.getActiveConnections() 
            ? choice1 
            : choice2;
    }

    @Override
    public String name() {
        return "ml-predictive";
    }

    /**
     * Immutable cache for ML predictions.
     * Uses volatile reference for thread-safe visibility without locks.
     */
    private static class PredictionCache {
        private final List<String> ranking;
        private final long timestamp;

        PredictionCache(List<String> ranking, long timestamp) {
            this.ranking = ranking;
            this.timestamp = timestamp;
        }
    }
}