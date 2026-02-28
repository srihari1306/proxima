import subprocess
import requests
import csv
import re
import time
import random
import statistics

# ==========================================
# CONFIG
# ==========================================

LB_URL = "http://localhost:8080"

BACKENDS = [
    "http://localhost:8081",
    "http://localhost:8082",
    "http://localhost:8083",
]

STRATEGIES = [
    "round-robin",
    "least-connections",
    "ml-predictive",
    "ewma-predictive",
]

RUNS_PER_STRATEGY = 5
COOLDOWN_SECONDS = 3

RAW_RESULT_FILE = "benchmark_raw.csv"
AGG_RESULT_FILE = "benchmark_aggregated.csv"

# ==========================================
# Utilities
# ==========================================

def set_strategy(name):
    requests.post(f"{LB_URL}/admin/strategy?name={name}", timeout=5)
    time.sleep(2)

def run_hey(n, c):
    cmd = ["hey", "-n", str(n), "-c", str(c), f"{LB_URL}/api/process"]
    result = subprocess.run(cmd, capture_output=True, text=True)
    return result.stdout

def parse_metrics(output):
    def extract(pattern):
        m = re.search(pattern, output)
        return float(m.group(1)) if m else None

    p50 = extract(r"50%.*?in\s+([0-9.]+)")
    p95 = extract(r"95%.*?in\s+([0-9.]+)")
    p99 = extract(r"99%.*?in\s+([0-9.]+)")
    rps = extract(r"Requests/sec:\s+([0-9.]+)")

    if None in (p50, p95, p99, rps):
        raise RuntimeError("Failed to parse hey output")

    return p50, p95, p99, rps

def warmup():
    run_hey(500, 50)
    time.sleep(2)

def reset_backends():
    for b in BACKENDS:
        requests.post(f"{b}/control/recover", timeout=5)
        requests.post(
            f"{b}/control/config",
            json={"base_latency": 50, "jitter": 10},
            timeout=5
        )
    time.sleep(3)

# ==========================================
# Core Benchmark Runner
# ==========================================

def run_scenario(name, hey_n, hey_c, setup_fn=None):
    """
    setup_fn: optional function executed before each run
    """
    raw_rows = []
    agg_rows = []

    print(f"\n{'='*60}")
    print(f"Running Scenario: {name}")
    print(f"{'='*60}")

    # Randomize order to remove bias
    randomized_strategies = STRATEGIES.copy()
    random.shuffle(randomized_strategies)

    for strategy in randomized_strategies:

        print(f"\nStrategy: {strategy}")

        metrics_runs = []

        for run_id in range(1, RUNS_PER_STRATEGY + 1):
            print(f"  Run {run_id}/{RUNS_PER_STRATEGY}")

            reset_backends()
            set_strategy(strategy)
            warmup()

            if setup_fn:
                setup_fn()

            output = run_hey(hey_n, hey_c)
            metrics = parse_metrics(output)
            metrics_runs.append(metrics)

            raw_rows.append([strategy, name, run_id, *metrics])

            time.sleep(COOLDOWN_SECONDS)

        # Aggregate results
        p50s = [m[0] for m in metrics_runs]
        p95s = [m[1] for m in metrics_runs]
        p99s = [m[2] for m in metrics_runs]
        rpss = [m[3] for m in metrics_runs]

        agg_rows.append([
            strategy,
            name,
            statistics.mean(p50s),
            statistics.stdev(p50s),
            statistics.mean(p95s),
            statistics.stdev(p95s),
            statistics.mean(p99s),
            statistics.stdev(p99s),
            statistics.mean(rpss),
            statistics.stdev(rpss),
        ])

    return raw_rows, agg_rows

# ==========================================
# Scenario Definitions
# ==========================================

def s1_setup():
    pass

def s3_setup():
    requests.post(
        f"{BACKENDS[0]}/control/degrade?target=800&duration=40",
        timeout=5
    )
    time.sleep(5)

def s4_setup():
    requests.post(
        f"{BACKENDS[0]}/control/config",
        json={"base_latency": 50, "jitter": 300},
        timeout=5
    )
    time.sleep(2)

def s5_setup():
    requests.post(
        f"{BACKENDS[0]}/control/failure",
        json={"type": "ERROR"},
        timeout=5
    )
    time.sleep(2)

def s6_setup():
    requests.post(
        f"{BACKENDS[0]}/control/degrade?target=400&duration=40",
        timeout=5
    )
    requests.post(
        f"{BACKENDS[1]}/control/degrade?target=600&duration=40",
        timeout=5
    )
    time.sleep(5)

def s7_setup():
    # Heterogeneous capacity
    requests.post(
        f"{BACKENDS[0]}/control/config",
        json={"base_latency": 30, "jitter": 5},
        timeout=5
    )
    requests.post(
        f"{BACKENDS[1]}/control/config",
        json={"base_latency": 30, "jitter": 5},
        timeout=5
    )
    requests.post(
        f"{BACKENDS[2]}/control/config",
        json={"base_latency": 80, "jitter": 20},
        timeout=5
    )
    time.sleep(2)

# ==========================================
# MAIN
# ==========================================

if __name__ == "__main__":

    all_raw = []
    all_agg = []

    scenarios = [
        ("S1-Stable", 3000, 100, s1_setup),
        ("S2-HighConcurrency", 6000, 200, s1_setup),
        ("S3-SingleDegradation", 4000, 100, s3_setup),
        ("S4-HighJitter", 4000, 100, s4_setup),
        ("S5-BackendFailure", 4000, 100, s5_setup),
        ("S6-MultipleDegradation", 5000, 150, s6_setup),
        ("S7-HeterogeneousCapacity", 5000, 150, s7_setup),
    ]

    for name, n, c, setup in scenarios:
        raw, agg = run_scenario(name, n, c, setup)
        all_raw.extend(raw)
        all_agg.extend(agg)

    # Write raw results
    with open(RAW_RESULT_FILE, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([
            "strategy", "scenario", "run",
            "p50", "p95", "p99", "rps"
        ])
        writer.writerows(all_raw)

    # Write aggregated results
    with open(AGG_RESULT_FILE, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([
            "strategy", "scenario",
            "mean_p50", "std_p50",
            "mean_p95", "std_p95",
            "mean_p99", "std_p99",
            "mean_rps", "std_rps"
        ])
        writer.writerows(all_agg)

    print("\n✅ Benchmark suite complete.")