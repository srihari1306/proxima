package com.example.load_balancer.strategy;

import com.example.load_balancer.model.BackendNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component("ewmaPredictiveStrategy")
public class EWMAPredictiveStrategy implements RoutingStrategy {

    private static final double EWMA_ALPHA = 0.4;
    private static final long TREND_WINDOW_MS = 5_000;
    private static final double TREND_PENALTY_FACTOR = 5.0;
    private static final double CONNECTION_PENALTY = 2.0;

    // Per-backend EWMA tracker
    private final Map<String, EWMATracker> ewmaTrackers = new ConcurrentHashMap<>();

    // Per-backend latency history (for trend detection)
    private final Map<String, Deque<LatencySnapshot>> latencyHistory = new ConcurrentHashMap<>();

    private static final int HISTORY_LIMIT = 100;

    // ============================================================
    // Routing Decision
    // ============================================================

    @Override
    public BackendNode select(List<BackendNode> candidates) {

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .min(Comparator.comparingDouble(this::calculateScore))
                .orElse(candidates.get(0));
    }

    private double calculateScore(BackendNode backend) {

        String id = backend.getId();

        double ewmaScore = getEWMAScore(id);
        double trend = getLatencyTrend(id);

        double score = ewmaScore
                + (Math.max(0, trend) * TREND_PENALTY_FACTOR)
                + (backend.getActiveConnections() * CONNECTION_PENALTY);

        return score;
    }

    // ============================================================
    // After Request Hook (Very Important)
    // ============================================================

    @Override
    public void after(BackendNode backend, long latency, boolean success) {

        if (!success) return;

        String id = backend.getId();

        // Update EWMA
        ewmaTrackers
                .computeIfAbsent(id, k -> new EWMATracker(EWMA_ALPHA))
                .update(latency);

        // Update history
        latencyHistory
                .computeIfAbsent(id, k -> new ArrayDeque<>(HISTORY_LIMIT));

        Deque<LatencySnapshot> history = latencyHistory.get(id);

        synchronized (history) {

            if (history.size() >= HISTORY_LIMIT) {
                history.removeFirst();
            }

            history.addLast(new LatencySnapshot(
                    System.currentTimeMillis(),
                    latency
            ));
        }
    }

    // ============================================================
    // EWMA Score
    // ============================================================

    private double getEWMAScore(String backendId) {

        EWMATracker tracker = ewmaTrackers.get(backendId);

        if (tracker == null) {
            return 0.0;
        }

        return tracker.getScore();
    }

    // ============================================================
    // Trend Calculation (Derivative)
    // ============================================================

    private double getLatencyTrend(String backendId) {

        Deque<LatencySnapshot> history = latencyHistory.get(backendId);

        if (history == null || history.size() < 2) {
            return 0.0;
        }

        LatencySnapshot current;
        LatencySnapshot past = null;

        synchronized (history) {

            current = history.peekLast();
            if (current == null) return 0.0;

            long targetTime = current.timestamp - TREND_WINDOW_MS;

            Iterator<LatencySnapshot> iterator = history.descendingIterator();

            while (iterator.hasNext()) {
                LatencySnapshot snap = iterator.next();
                if (snap.timestamp <= targetTime) {
                    past = snap;
                    break;
                }
            }
        }

        if (past == null) {
            return 0.0;
        }

        double latencyDelta = current.latency - past.latency;
        double timeDeltaSec = (current.timestamp - past.timestamp) / 1000.0;

        if (timeDeltaSec < 1.0) {
            return 0.0;
        }

        return latencyDelta / timeDeltaSec;
    }

    @Override
    public String name() {
        return "ewma-predictive";
    }

    // ============================================================
    // Helper Classes
    // ============================================================

    private static class EWMATracker {

        private double score;
        private final double alpha;
        private boolean initialized = false;

        public EWMATracker(double alpha) {
            this.alpha = alpha;
        }

        public double update(double newLatency) {

            if (!initialized) {
                score = newLatency;
                initialized = true;
            } else {
                score = alpha * newLatency
                        + (1 - alpha) * score;
            }

            return score;
        }

        public double getScore() {
            return score;
        }
    }

    private static class LatencySnapshot {

        final long timestamp;
        final double latency;

        LatencySnapshot(long timestamp, double latency) {
            this.timestamp = timestamp;
            this.latency = latency;
        }
    }
}