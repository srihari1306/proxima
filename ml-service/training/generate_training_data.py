import requests
import random
import time
from concurrent.futures import ThreadPoolExecutor

LB_URL = "http://localhost:8080"
BACKENDS = [8081, 8082, 8083]


def configure_backends():
    for port in BACKENDS:
        base_latency = random.randint(30, 120)
        jitter = random.randint(5, 25)

        requests.post(
            f"http://localhost:{port}/control/config",
            json={
                "baseLatency": base_latency,
                "jitter": jitter
            }
        )


def generate_traffic(duration_seconds, concurrency):

    def hit():
        try:
            requests.get(f"{LB_URL}/api/process", timeout=5)
        except:
            pass

    end = time.time() + duration_seconds

    with ThreadPoolExecutor(max_workers=100) as executor:
        while time.time() < end:
            for _ in range(concurrency):
                executor.submit(hit)
            time.sleep(0.1)


def main():
    print("Setting strategy to round-robin")
    requests.post(f"{LB_URL}/admin/strategy?name=round-robin")

    print("Starting logging")
    requests.post(f"{LB_URL}/admin/start-logging?file=train.csv")

    # Phase 1
    configure_backends()
    generate_traffic(duration_seconds=30, concurrency=5)

    # Phase 2
    configure_backends()
    generate_traffic(duration_seconds=45, concurrency=20)

    # Phase 3
    configure_backends()
    generate_traffic(duration_seconds=60, concurrency=50)

    print("Stopping logging")
    requests.post(f"{LB_URL}/admin/stop-logging")

    print("Training data generation complete")


if __name__ == "__main__":
    main()