import os
import random
import time
from locust import HttpUser, task, constant_pacing, events

MASTER_URL = "http://localhost:8080"
REPLICA1_URL = "http://localhost:8081"
REPLICA2_URL = "http://localhost:8082"
KEYSPACE = 300
READ_FROM_MASTER_RATIO = 0.2

stats = {
    "stale_replica_reads": 0,
    "read_checks_failed": 0,
    "write_checks_failed": 0,
}
expected_state = {}


def random_key() -> str:
    return str(random.randint(1, KEYSPACE))


def random_value() -> str:
    return f"value-{random.randint(1, 1_000_000)}"


def pick_read_base_url() -> str:
    if random.random() < READ_FROM_MASTER_RATIO:
        return MASTER_URL
    return random.choice([REPLICA1_URL, REPLICA2_URL])


class BTreeUser(HttpUser):
    wait_time = constant_pacing(1.0)
    host = MASTER_URL

    @task(25)
    def insert(self):
        key = random_key()
        value = random_value()
        with self.client.post(
            "/api/tree/insert",
            json={"key": key, "value": value},
            name="POST /api/tree/insert",
            catch_response=True,
        ) as response:
            if response.status_code in (200, 201, 204):
                expected_state[key] = {"value": value, "updated_at": time.time()}
                response.success()
            else:
                stats["write_checks_failed"] += 1
                response.failure(f"Unexpected status: {response.status_code}")

    @task(15)
    def remove(self):
        key = random_key()
        with self.client.delete(
            f"/api/tree/{key}",
            name="DELETE /api/tree/{key}",
            catch_response=True,
        ) as response:
            if response.status_code == 200:
                try:
                    body = response.json()
                    if body.get("removed") is True:
                        expected_state.pop(key, None)
                    response.success()
                except Exception as exc:
                    stats["write_checks_failed"] += 1
                    response.failure(f"JSON parse error: {exc}")
            else:
                stats["write_checks_failed"] += 1
                response.failure(f"Unexpected status: {response.status_code}")

    @task(35)
    def get_value(self):
        key = random_key()
        base_url = pick_read_base_url()
        expected = expected_state.get(key)

        with self.client.get(
            f"{base_url}/api/tree/{key}",
            name="GET /api/tree/{key}",
            catch_response=True,
        ) as response:
            if response.status_code == 200:
                try:
                    body = response.json()
                    actual_value = body.get("value")
                    if expected and actual_value == expected["value"]:
                        response.success()
                    elif base_url != MASTER_URL:
                        stats["stale_replica_reads"] += 1
                        response.success()
                    else:
                        stats["read_checks_failed"] += 1
                        response.failure(f"Unexpected value. expected={expected}, actual={actual_value}")
                except Exception as exc:
                    stats["read_checks_failed"] += 1
                    response.failure(f"JSON parse error: {exc}")
            elif response.status_code == 404:
                if expected and base_url == MASTER_URL:
                    stats["read_checks_failed"] += 1
                    response.failure("Master returned 404 for expected existing key")
                else:
                    if expected and base_url != MASTER_URL:
                        stats["stale_replica_reads"] += 1
                    response.success()
            else:
                stats["read_checks_failed"] += 1
                response.failure(f"Unexpected status: {response.status_code}")

    @task(25)
    def exists(self):
        key = random_key()
        base_url = pick_read_base_url()
        expected_exists = key in expected_state

        with self.client.get(
            f"{base_url}/api/tree/{key}/exists",
            name="GET /api/tree/{key}/exists",
            catch_response=True,
        ) as response:
            if response.status_code != 200:
                stats["read_checks_failed"] += 1
                response.failure(f"Unexpected status: {response.status_code}")
                return

            try:
                body = response.json()
                actual_exists = body.get("exists")
                if actual_exists == expected_exists:
                    response.success()
                elif base_url != MASTER_URL:
                    stats["stale_replica_reads"] += 1
                    response.success()
                else:
                    stats["read_checks_failed"] += 1
                    response.failure(f"Master exists mismatch. expected={expected_exists}, actual={actual_exists}")
            except Exception as exc:
                stats["read_checks_failed"] += 1
                response.failure(f"JSON parse error: {exc}")


@events.quitting.add_listener
def _(environment, **kwargs):
    print("\n=== Custom summary ===")
    print(f"stale_replica_reads: {stats['stale_replica_reads']}")
    print(f"read_checks_failed: {stats['read_checks_failed']}")
    print(f"write_checks_failed: {stats['write_checks_failed']}")
    print("======================\n")
