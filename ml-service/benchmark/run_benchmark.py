# import subprocess
# import requests
# import csv
# import re
# import time

# LB_URL = "http://localhost:8080"
# STRATEGIES = ["round-robin", "least-connections", "ml-predictive"]

# RESULT_FILE = "benchmark_results.csv"

# def set_strategy(name):
#     requests.post(f"{LB_URL}/admin/strategy?name={name}")

# def run_hey(n, c):
#     cmd = ["hey", "-n", str(n), "-c", str(c), f"{LB_URL}/api/process"]
#     result = subprocess.run(cmd, capture_output=True, text=True)
#     return result.stdout

# def parse_metrics(output):
#     def extract(pattern):
#         match = re.search(pattern, output)
#         if match:
#             return float(match.group(1))
#         return None

#     p50 = extract(r"50%.*?in\s+([0-9.]+)")
#     p95 = extract(r"95%.*?in\s+([0-9.]+)")
#     p99 = extract(r"99%.*?in\s+([0-9.]+)")
#     rps = extract(r"Requests/sec:\s+([0-9.]+)")

#     if None in (p50, p95, p99, rps):
#         print("Could not parse hey output. Raw output:")
#         print(output)
#         raise ValueError("Parsing failed")

#     return p50, p95, p99, rps

# def benchmark_s1():
#     results = []

#     for strategy in STRATEGIES:
#         print(f"Testing {strategy}")
#         set_strategy(strategy)
#         time.sleep(2)

#         output = run_hey(2000, 50)
#         p50, p95, p99, rps = parse_metrics(output)

#         results.append([strategy, "S1-Stable", p50, p95, p99, rps])

#     return results

# def save_results(rows):
#     with open(RESULT_FILE, "w", newline="") as f:
#         writer = csv.writer(f)
#         writer.writerow(["strategy", "scenario", "p50", "p95", "p99", "rps"])
#         writer.writerows(rows)

# if __name__ == "__main__":
#     rows = benchmark_s1()
#     save_results(rows)
#     print("Benchmark complete.")

import subprocess
import requests
import csv
import re
import time

LB_URL = "http://localhost:8080"
BACKEND_1 = "http://localhost:8081"
STRATEGIES = ["round-robin", "least-connections", "ml-predictive"]

RESULT_FILE = "benchmark_results.csv"


# -------------------------
# Utilities
# -------------------------

def set_strategy(name):
    requests.post(f"{LB_URL}/admin/strategy?name={name}")
    time.sleep(2)

def run_hey(n, c):
    cmd = ["hey", "-n", str(n), "-c", str(c), f"{LB_URL}/api/process"]
    result = subprocess.run(cmd, capture_output=True, text=True)
    return result.stdout

def parse_metrics(output):
    def extract(pattern):
        match = re.search(pattern, output)
        return float(match.group(1)) if match else None

    p50 = extract(r"50%.*?in\s+([0-9.]+)")
    p95 = extract(r"95%.*?in\s+([0-9.]+)")
    p99 = extract(r"99%.*?in\s+([0-9.]+)")
    rps = extract(r"Requests/sec:\s+([0-9.]+)")

    if None in (p50, p95, p99, rps):
        print("Parsing failed. Raw output:")
        print(output)
        raise ValueError("Could not parse hey output")

    return p50, p95, p99, rps

def reset_backends():
    requests.post(f"{BACKEND_1}/control/recover")
    time.sleep(2)


# -------------------------
# Scenarios
# -------------------------

def scenario_s2():
    rows = []
    for strategy in STRATEGIES:
        print(f"S2 - High Concurrency - {strategy}")
        set_strategy(strategy)
        output = run_hey(5000, 200)
        rows.append([strategy, "S2-HighConcurrency", *parse_metrics(output)])
    return rows


def scenario_s3():
    rows = []
    for strategy in STRATEGIES:
        print(f"S3 - Single Degradation - {strategy}")
        set_strategy(strategy)

        # degrade backend-1
        requests.post(f"{BACKEND_1}/control/degrade?target=800&duration=30")
        time.sleep(2)

        output = run_hey(3000, 100)
        rows.append([strategy, "S3-SingleDegradation", *parse_metrics(output)])

        reset_backends()

    return rows


def scenario_s4():
    rows = []
    for strategy in STRATEGIES:
        print(f"S4 - High Jitter - {strategy}")
        set_strategy(strategy)

        # High jitter on backend-1
        requests.post(
            f"{BACKEND_1}/control/config",
            json={"base_latency": 50, "jitter": 300}
        )
        time.sleep(2)

        output = run_hey(3000, 100)
        rows.append([strategy, "S4-HighJitter", *parse_metrics(output)])

        # reset jitter
        requests.post(
            f"{BACKEND_1}/control/config",
            json={"base_latency": 50, "jitter": 10}
        )
        time.sleep(2)

    return rows


def scenario_s5():
    rows = []
    for strategy in STRATEGIES:
        print(f"S5 - Backend Failure - {strategy}")
        set_strategy(strategy)

        # Force failure
        requests.post(f"{BACKEND_1}/control/failure?mode=RETURN_ERROR")
        time.sleep(2)

        output = run_hey(3000, 100)
        rows.append([strategy, "S5-BackendFailure", *parse_metrics(output)])

        reset_backends()

    return rows


# -------------------------
# Main
# -------------------------

if __name__ == "__main__":
    all_rows = []

    # S2
    all_rows.extend(scenario_s2())

    # S3
    all_rows.extend(scenario_s3())

    # S4
    all_rows.extend(scenario_s4())

    # S5
    all_rows.extend(scenario_s5())

    with open(RESULT_FILE, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["strategy", "scenario", "p50", "p95", "p99", "rps"])
        writer.writerows(all_rows)

    print("All benchmarks complete.")