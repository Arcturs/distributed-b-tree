import random
import time
from locust import HttpUser, task, constant_pacing, events

NODE1 = "http://localhost:8080"
NODE2 = "http://localhost:8081"
NODE3 = "http://localhost:8082"

NODES = [NODE1, NODE2, NODE3]
KEYSPACE = 300

SUCCESS_STATUSES = (200, 201, 204)

stats = {
    "stale_or_pending_reads": 0,
    "conflict_reads": 0,
    "read_checks_failed": 0,
    "write_checks_failed": 0,
}

conflict_keys = set()
expected_state = {}


def node_url() -> str:
    return random.choice(NODES)


def random_key() -> str:
    return str(random.randint(1, KEYSPACE))


def random_value() -> str:
    return f"value-{random.randint(1, 1_000_000)}"


def mark_inserted(key: str, value: str):
    expected_state[key] = {
        "values": {value},
        "deleted": False,
        "updated_at": time.time(),
    }


def mark_deleted(key: str):
    expected_state[key] = {
        "values": set(),
        "deleted": True,
        "updated_at": time.time(),
    }


def normalize_value(value):
    if value is None:
        return None

    if isinstance(value, dict):
        if "value" in value:
            return normalize_value(value.get("value"))
        if "val" in value:
            return normalize_value(value.get("val"))
        return str(value)

    return str(value)


def extract_values(body) -> set[str]:
    if body is None:
        return set()

    values = set()

    if isinstance(body, list):
        for item in body:
            normalized = normalize_value(item)
            if normalized is not None:
                values.add(normalized)
        return values

    if not isinstance(body, dict):
        normalized = normalize_value(body)
        return {normalized} if normalized is not None else set()

    value_fields = (
        "value",
        "values",
        "conflicts",
        "conflictValues",
        "conflict_values",
        "siblings",
        "candidates",
    )

    for field in value_fields:
        if field not in body:
            continue

        raw = body.get(field)

        if isinstance(raw, list):
            for item in raw:
                normalized = normalize_value(item)
                if normalized is not None:
                    values.add(normalized)
        else:
            normalized = normalize_value(raw)
            if normalized is not None:
                values.add(normalized)

    return values


def has_conflict(body, values: set[str]) -> bool:
    if isinstance(body, dict):
        explicit_conflict = (
            body.get("conflict") is True
            or body.get("conflicted") is True
            or body.get("hasConflict") is True
            or body.get("has_conflict") is True
        )

        if explicit_conflict:
            return True

    return len(values) > 1


def record_conflict_if_needed(key: str, body) -> set[str]:
    values = extract_values(body)

    if has_conflict(body, values):
        stats["conflict_reads"] += 1
        conflict_keys.add(key)

    return values


class CrdtUser(HttpUser):
    host = NODE1
    wait_time = constant_pacing(1.0)

    @task(30)
    def put_value(self):
        key = random_key()
        value = random_value()
        base_url = node_url()

        with self.client.post(
            f"{base_url}/api/crdt/tree/insert",
            json={"key": key, "value": value},
            name="POST /api/crdt/tree/insert",
            catch_response=True,
        ) as response:
            if response.status_code in SUCCESS_STATUSES:
                mark_inserted(key, value)
                response.success()
            else:
                stats["write_checks_failed"] += 1
                response.failure(f"Unexpected status: {response.status_code}")

    @task(15)
    def remove_value(self):
        key = random_key()
        base_url = node_url()

        with self.client.delete(
            f"{base_url}/api/crdt/tree/{key}",
            name="DELETE /api/crdt/tree/{key}",
            catch_response=True,
        ) as response:
            if response.status_code in SUCCESS_STATUSES:
                mark_deleted(key)
                response.success()
            else:
                stats["write_checks_failed"] += 1
                response.failure(f"Unexpected status: {response.status_code}")

    @task(35)
    def get_value(self):
        key = random_key()
        base_url = node_url()
        expected = expected_state.get(key)

        with self.client.get(
            f"{base_url}/api/crdt/tree/{key}",
            name="GET /api/crdt/tree/{key}",
            catch_response=True,
        ) as response:
            if response.status_code == 200:
                try:
                    body = response.json()
                    actual_values = record_conflict_if_needed(key, body)

                    if expected is None:
                        response.success()
                        return

                    if expected["deleted"]:
                        if actual_values:
                            stats["stale_or_pending_reads"] += 1
                        response.success()
                        return

                    expected_values = expected["values"]

                    if actual_values & expected_values:
                        response.success()
                    else:
                        stats["stale_or_pending_reads"] += 1
                        response.success()

                except Exception as exc:
                    stats["read_checks_failed"] += 1
                    response.failure(f"JSON parse error: {exc}")

            elif response.status_code == 404:
                if expected and not expected["deleted"]:
                    stats["stale_or_pending_reads"] += 1

                response.success()

            else:
                stats["read_checks_failed"] += 1
                response.failure(f"Unexpected status: {response.status_code}")

    @task(20)
    def exists(self):
        key = random_key()
        base_url = node_url()
        expected = expected_state.get(key)

        if expected is None:
            expected_exists = None
        else:
            expected_exists = not expected["deleted"]

        with self.client.get(
            f"{base_url}/api/crdt/tree/{key}/exists",
            name="GET /api/crdt/tree/{key}/exists",
            catch_response=True,
        ) as response:
            if response.status_code != 200:
                stats["read_checks_failed"] += 1
                response.failure(f"Unexpected status: {response.status_code}")
                return

            try:
                body = response.json()
                actual_exists = body.get("exists")

                if expected_exists is None:
                    response.success()
                    return

                if actual_exists == expected_exists:
                    response.success()
                else:
                    stats["stale_or_pending_reads"] += 1
                    response.success()

            except Exception as exc:
                stats["read_checks_failed"] += 1
                response.failure(f"JSON parse error: {exc}")


@events.quitting.add_listener
def _(environment, **kwargs):
    print("\n=== CRDT custom summary ===")
    print(f"stale_or_pending_reads: {stats['stale_or_pending_reads']}")
    print(f"conflict_reads: {stats['conflict_reads']}")
    print(f"conflict_keys: {len(conflict_keys)}")
    print(f"read_checks_failed: {stats['read_checks_failed']}")
    print(f"write_checks_failed: {stats['write_checks_failed']}")
    print(f"tracked_expected_keys: {len(expected_state)}")
    print("===========================\n")