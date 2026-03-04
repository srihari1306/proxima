# Proxima – Adaptive Load Balancer with Predictive Routing

> An experimental load balancing framework for studying predictive vs. traditional routing strategies under dynamic distributed system conditions.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Components](#components)
  - [Load Balancer](#load-balancer)
  - [Backend Simulation Services](#backend-simulation-services)
  - [ML Service](#ml-service)
  - [Benchmarking Framework](#benchmarking-framework)
- [Routing Strategies](#routing-strategies)
- [Fault Injection API](#fault-injection-api)
- [Benchmark Scenarios](#benchmark-scenarios)
- [Metrics & Outputs](#metrics--outputs)
- [Key Findings](#key-findings)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)

---

## Overview

Proxima is a research platform for evaluating how load balancing strategies perform under realistic distributed system conditions including:

- Gradual backend performance degradation
- Latency jitter and noise
- Backend failures and outages
- Heterogeneous infrastructure capacity

**Research Question:** *When do predictive routing strategies outperform traditional algorithms like Least Connections?*

---

## Architecture

```
Load Generator (hey)
        |
        v
Proxima Load Balancer        ←→   ML Service (Python/FastAPI)
  (strategy selection)
        |
        v
  Backend Services (×3)
  (latency simulation)
```

The load generator sends sustained HTTP traffic to the Proxima load balancer. The balancer routes each request using a pluggable strategy, and backend services simulate realistic distributed system behavior. A separate Python ML service provides latency predictions for the ML-based routing strategy.

---

## Components

### Load Balancer

**Stack:** Java + Spring Boot

The load balancer is the core of Proxima. It maintains a registry of backend nodes and routes incoming requests based on the active strategy.

**Key Endpoints:**

| Endpoint | Method | Description |
|---|---|---|
| `/api/process` | GET | Main routing endpoint — forwards requests to a backend |
| `/admin/strategy?name=<strategy>` | POST | Switch routing strategy at runtime |

**Backend Metrics Polling:**  
The load balancer periodically polls each backend's `/metrics` endpoint and updates its internal model of backend health and performance. These metrics feed directly into predictive routing strategies.

**Circuit Breaker:**  
A circuit breaker manager tracks backend failure rates and temporarily removes misbehaving backends from the routing pool.

**Request Logging:**  
Every routed request is logged with full context: strategy, backend ID, observed latency, active connections, and success/failure status. This log becomes the training dataset for the ML model.

---

### Backend Simulation Services

**Stack:** Java + Spring Boot (×3 instances)

Each backend simulates realistic distributed system behavior with configurable latency, jitter, and failure modes.

**Endpoints:**

| Endpoint | Method | Description |
|---|---|---|
| `/process` | GET | Simulates work; sleeps for calculated latency |
| `/metrics` | GET | Returns avg latency, last latency, active connections, request rate, queue size, active workers |
| `/health` | GET | Returns `UP` or `DOWN` (503) |
| `/control/config` | POST | Set base latency and jitter dynamically |
| `/control/degrade` | POST | Start gradual latency degradation |
| `/control/failure` | POST | Inject failure mode (ERROR or timeout) |
| `/control/recover` | POST | Reset to healthy state |

**Latency Simulation:**

```
latency = base_latency + random_jitter
```

Degradation linearly increases latency from the base value to a target over a specified duration, simulating real-world issues like memory leaks, CPU saturation, and resource exhaustion.

**Worker Pool:**  
Each backend uses a bounded worker pool. Requests exceeding queue capacity return `503 Queue Full`, simulating realistic backpressure.

---

### ML Service

**Stack:** Python + FastAPI + XGBoost

The ML service exposes a `/predict` endpoint that accepts backend metrics and returns latency predictions and a ranked list of backends.

**Endpoints:**

| Endpoint | Method | Description |
|---|---|---|
| `/predict` | POST | Returns predicted latency per backend + ranked order |
| `/health` | GET | Health check |

**Model:** XGBoost Regressor trained on historical request logs.

**Features:**

| Feature | Description |
|---|---|
| `active_connections` | Current in-flight requests |
| `avg_latency` | Rolling average latency (ms) |
| `last_latency` | Most recent observed latency (ms) |
| `request_rate` | Requests per second |

**Target:** `observed_latency` — actual end-to-end latency measured at the load balancer.

**Training pipeline** (`train.py`):
- Loads labeled request logs from `train.csv`
- Trains XGBoost with 5-fold cross-validation
- Reports MAE, RMSE, R²
- Saves model to `ml-service/models/latency_predictor.pkl`

---

### Benchmarking Framework

**Stack:** Python + `hey` load generator

The benchmarking framework orchestrates full experiments: it configures backends, selects strategies, generates sustained traffic with `hey`, injects faults at precise times, parses results, and writes structured output CSVs.

**Traffic configuration:**

```bash
hey -z 60s -c 120 http://localhost:8080/api/process
```

- `TEST_DURATION = 60s` per scenario per strategy
- Fault injected at `t = 20s`
- Normal concurrency: 120 workers
- High concurrency: 250 workers

---

## Routing Strategies

### Round Robin
Distributes requests sequentially across all healthy backends.
- **Pros:** Deterministic, zero overhead, useful as a baseline
- **Cons:** Ignores backend performance entirely

### Least Connections
Routes to the backend with the fewest active in-flight connections.
- **Pros:** Reacts to overload; commonly used in production
- **Cons:** Reactive only — cannot detect gradual latency drift before it worsens

### EWMA Predictive Routing
Uses an Exponentially Weighted Moving Average of observed latency to score backends.

```
EWMA = α * current_latency + (1 - α) * previous_EWMA
```

**Score formula:**
```
score = EWMA + max(0, latency_trend) × TREND_PENALTY + active_connections × CONNECTION_PENALTY
```

| Parameter | Value |
|---|---|
| `EWMA_ALPHA` | 0.4 |
| `TREND_WINDOW_MS` | 5000ms |
| `TREND_PENALTY_FACTOR` | 5.0 |
| `CONNECTION_PENALTY` | 2.0 |

The trend component detects rising latency early — before the average degrades enough to be visible to Least Connections.

### ML Predictive Routing
Uses an external XGBoost model to predict backend latency, combined with several production-grade techniques:

- **Epsilon-greedy exploration (10%):** Sends random traffic to prevent backend starvation and enable recovery detection
- **Top-K selection (K=3):** Only considers the top-ranked backends from the ML prediction
- **Power of Two Choices:** Within the top-K pool, samples two candidates randomly and picks the one with fewer active connections
- **Prediction caching (200ms TTL):** Limits ML service call rate under high throughput
- **Automatic fallback:** Falls back to Least Connections if the ML service times out or returns an error

> **Note:** This is intentionally a distributed architecture. The ML service overhead is part of the evaluation — measuring when the prediction benefit outweighs the inference cost is a core research goal.

---

## Fault Injection API

Backends expose a control API for precise fault injection during benchmarks:

**Gradual Degradation:**
```bash
POST /control/degrade?target=300&duration=40
```
Linearly increases latency from base to 300ms over 40 seconds.

**Failure Injection:**
```bash
POST /control/failure
```
Switches the backend to return errors or timeouts.

**Config Update:**
```bash
POST /control/config
Content-Type: application/json

{"base_latency": 50, "jitter": 10}
```

**Recovery:**
```bash
POST /control/recover
```

---

## Benchmark Scenarios

| Scenario | Description |
|---|---|
| **S1 – Stable Load** | All backends healthy. Baseline performance measurement. |
| **S2 – High Concurrency** | Heavy traffic (250 workers). Scalability under load. |
| **S3 – Gradual Degradation** | Backend-1 degrades from 50ms → 300ms over 40s. Tests early detection. |
| **S4 – Multiple Degradation** | Backend-1 → 250ms, Backend-2 → 350ms simultaneously. Tests ranking. |
| **S5 – Backend Failure** | Backend-1 starts returning errors. Tests fault tolerance. |
| **S6 – High Jitter** | Backend-1 jitter increases to ±300ms. Tests noise stability. |
| **S7 – Heterogeneous Capacity** | Backends 1 & 2 at 30ms base, Backend-3 at 80ms. Tests adaptive distribution. |
| **S8 – Recovery Detection** | Backend-1 degrades to 400ms, then recovers. Tests rebalancing. |

All scenarios run all four strategies back-to-back with full backend resets and warmup between runs.

---

## Metrics & Outputs

**Benchmark outputs three CSV files:**

| File | Contents |
|---|---|
| `final_benchmark_results.csv` | Summary: strategy, scenario, p50, p95, p99, RPS |
| `time_series_results.csv` | Per-second telemetry: per-backend traffic share, connections, latency |
| `reaction_times.csv` | Time to shift <5% traffic to degraded backend per strategy |

**Reaction Time Definition:**

> Time elapsed from fault injection until the degraded backend receives less than 5% of total traffic.

This metric is the clearest differentiator between reactive and predictive strategies.

---

## Key Findings

- **Least Connections performs near-optimally** when all backends have symmetric performance — predictive strategies add overhead without benefit.
- **EWMA Predictive routing detects degradation earlier** than Least Connections due to latency trend awareness, yielding better p99 during gradual degradation scenarios.
- **Tail latency (p99) is the most sensitive metric** for detecting routing strategy differences — averages often mask improvements.
- **ML routing inference overhead is a real cost** — under high throughput, the 200ms prediction cache and 50ms timeout limit the benefit, particularly in short-burst scenarios.
- **Exploration (ε-greedy) is necessary** for ML routing to detect backend recovery; without it, recovered backends can remain starved.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Load Balancer | Java 17, Spring Boot |
| Backend Services | Java 17, Spring Boot |
| ML Service | Python, FastAPI, XGBoost, scikit-learn |
| Load Generator | `hey` |
| Benchmarking | Python |
| Model Serialization | `joblib` |

---

## Getting Started

### Prerequisites

- Java 17+
- Python 3.9+
- [`hey`](https://github.com/rakyll/hey) installed and on PATH

### 1. Start Backend Services

Run three instances on ports 8081, 8082, 8083:

```bash
# Backend 1
SERVER_PORT=8081 ./mvnw spring-boot:run -pl backend-service

# Backend 2
SERVER_PORT=8082 ./mvnw spring-boot:run -pl backend-service

# Backend 3
SERVER_PORT=8083 ./mvnw spring-boot:run -pl backend-service
```

### 2. Start the Load Balancer

```bash
./mvnw spring-boot:run -pl load-balancer
# Runs on port 8080
```

### 3. Start the ML Service

```bash
cd ml-service
pip install -r requirements.txt
python api.py
# Runs on port 8000
```

> **Note:** Train the model first if `latency_predictor.pkl` does not exist:
> ```bash
> cd ml-service/training
> python train.py
> ```

### 4. Run the Benchmark Suite

```bash
cd benchmarking
pip install -r requirements.txt
python benchmark.py
```

Results are written to `final_benchmark_results.csv`, `time_series_results.csv`, and `reaction_times.csv`.

### 5. Switch Strategy Manually

```bash
curl -X POST "http://localhost:8080/admin/strategy?name=ewma-predictive"
```

Available strategy names: `round-robin`, `least-connections`, `ewma-predictive`, `ml-predictive`
