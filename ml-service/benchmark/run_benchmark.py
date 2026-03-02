import subprocess
import requests
import csv
import re
import time

# ==========================================
# CONFIGURATION
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
    "ewma-predictive",
    "ml-predictive",
]

RESULT_FILE = "final_benchmark_results.csv"

DEFAULT_LATENCY = 50
DEFAULT_JITTER = 10

TEST_DURATION = 60
INJECT_AT = 20
CONCURRENCY_NORMAL = 120
CONCURRENCY_HIGH = 250

# ==========================================
# UTILITIES
# ==========================================

def set_strategy(name):
    print(f"\n→ Strategy: {name}")
    r = requests.post(f"{LB_URL}/admin/strategy?name={name}", timeout=5)
    r.raise_for_status()
    time.sleep(2)

def run_hey_duration(seconds, concurrency):
    cmd = [
        "hey",
        "-z", f"{seconds}s",
        "-c", str(concurrency),
        f"{LB_URL}/api/process"
    ]
    return subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

def parse_metrics(output):
    def extract(pattern):
        m = re.search(pattern, output)
        return float(m.group(1)) if m else None

    return (
        extract(r"50%.*?in\s+([0-9.]+)"),
        extract(r"95%.*?in\s+([0-9.]+)"),
        extract(r"99%.*?in\s+([0-9.]+)"),
        extract(r"Requests/sec:\s+([0-9.]+)")
    )

def reset_backends():
    print("→ Resetting backends")
    for b in BACKENDS:
        requests.post(f"{b}/control/recover", timeout=5)
        requests.post(
            f"{b}/control/config",
            json={"base_latency": DEFAULT_LATENCY, "jitter": DEFAULT_JITTER},
            timeout=5
        )
    time.sleep(3)

def warmup():
    print("→ Warmup")
    subprocess.run(
        ["hey", "-z", "10s", "-c", "50", f"{LB_URL}/api/process"],
        stdout=subprocess.DEVNULL
    )
    time.sleep(2)

# ==========================================
# SCENARIO TEMPLATE
# ==========================================

def run_scenario(name, concurrency, injection_fn=None):
    rows = []

    print("\n" + "="*70)
    print(f"RUNNING SCENARIO: {name}")
    print("="*70)

    for strategy in STRATEGIES:

        reset_backends()
        set_strategy(strategy)
        warmup()

        print(f"→ Starting {TEST_DURATION}s load @ concurrency={concurrency}")
        process = run_hey_duration(TEST_DURATION, concurrency)

        if injection_fn:
            time.sleep(INJECT_AT)
            injection_fn()

        stdout, _ = process.communicate()

        p50, p95, p99, rps = parse_metrics(stdout)
        rows.append([strategy, name, p50, p95, p99, rps])

        print(f"✓ Completed: {strategy}")
        time.sleep(5)

    return rows

# ==========================================
# SCENARIO DEFINITIONS
# ==========================================

def s1_stable():
    return run_scenario("S1-Stable", CONCURRENCY_NORMAL)

def s2_high_concurrency():
    return run_scenario("S2-HighConcurrency", CONCURRENCY_HIGH)

def s3_gradual_degradation():
    def inject():
        print("→ Injecting gradual degradation on backend-1")
        requests.post(
            f"{BACKENDS[0]}/control/degrade?target=300&duration=40",
            timeout=5
        )
    return run_scenario("S3-GradualDegradation", CONCURRENCY_NORMAL, inject)

def s4_multiple_degradation():
    def inject():
        print("→ Injecting degradation on backend-1 and backend-2")
        requests.post(f"{BACKENDS[0]}/control/degrade?target=250&duration=40")
        requests.post(f"{BACKENDS[1]}/control/degrade?target=350&duration=40")
    return run_scenario("S4-MultipleDegradation", CONCURRENCY_NORMAL, inject)

def s5_backend_failure():
    def inject():
        print("→ Injecting backend failure")
        requests.post(
            f"{BACKENDS[0]}/control/failure",
            json={"type": "ERROR"}
        )
    return run_scenario("S5-BackendFailure", CONCURRENCY_NORMAL, inject)

def s6_high_jitter():
    def inject():
        print("→ Adding high jitter to backend-1")
        requests.post(
            f"{BACKENDS[0]}/control/config",
            json={"base_latency": 50, "jitter": 300}
        )
    return run_scenario("S6-HighJitter", CONCURRENCY_NORMAL, inject)

def s7_heterogeneous_capacity():
    def inject():
        print("→ Configuring heterogeneous backends")
        requests.post(
            f"{BACKENDS[0]}/control/config",
            json={"base_latency": 30, "jitter": 5}
        )
        requests.post(
            f"{BACKENDS[1]}/control/config",
            json={"base_latency": 30, "jitter": 5}
        )
        requests.post(
            f"{BACKENDS[2]}/control/config",
            json={"base_latency": 80, "jitter": 20}
        )
    return run_scenario("S7-HeterogeneousCapacity", CONCURRENCY_NORMAL, inject)

def s8_recovery_detection():
    def inject():
        print("→ Degrading backend-1 (recovery test)")
        requests.post(
            f"{BACKENDS[0]}/control/degrade?target=400&duration=20"
        )
    return run_scenario("S8-RecoveryDetection", CONCURRENCY_NORMAL, inject)

# ==========================================
# MAIN
# ==========================================

if __name__ == "__main__":

    print("\n" + "="*70)
    print("FINAL COMPREHENSIVE BENCHMARK SUITE")
    print("="*70)

    all_rows = []

    try:
        all_rows += s1_stable()
        all_rows += s2_high_concurrency()
        all_rows += s3_gradual_degradation()
        all_rows += s4_multiple_degradation()
        all_rows += s5_backend_failure()
        all_rows += s6_high_jitter()
        all_rows += s7_heterogeneous_capacity()
        all_rows += s8_recovery_detection()

        with open(RESULT_FILE, "w", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["strategy", "scenario", "p50", "p95", "p99", "rps"])
            writer.writerows(all_rows)

        print("\n✅ FINAL BENCHMARK COMPLETE")
        print(f"Results saved to: {RESULT_FILE}")

    except Exception as e:
        print(f"\n❌ ERROR: {e}")
        import traceback
        traceback.print_exc()