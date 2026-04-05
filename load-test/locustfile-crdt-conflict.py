import os
import random
import time
from locust import HttpUser, task, constant_pacing, events

NODE_URLS = [
    "http://localhost:8080",
    "http://localhost:8081",
    "http://localhost:8082"
]

HOT_KEY_COUNT = 5
NORMAL_KEY_COUNT = 1000

HOT_WRITE_RATIO = 0.85
HOT_READ_RATIO = 0.5

PUT_WEIGHT = 45
REMOVE_WEIGHT = 10
GET_WEIGHT = 30
EXISTS_WEIGHT = 15

# Burst-конфликты
BURST_ENABLED = True
BURST_PERIOD_SEC = 4.0
BURST_DURATION_SEC = 0.4
BURST_SAME_KEY_RATIO = 0.9

stats = {
    "conflict_reads": 0,
    "normal_reads": 0,
    "absent_reads": 0,
    "http_errors": 0,
}

def now_in_burst_window():
    if not BURST_ENABLED:
        return False
    t = time.time()
    return (t % BURST_PERIOD_SEC) < BURST_DURATION_SEC

def hot_key():
    return f"hot-{random.randint(1, HOT_KEY_COUNT)}"

def normal_key():
    return f"key-{random.randint(1, NORMAL_KEY_COUNT)}"

def choose_write_key():
    # В burst-окне почти все write бьют в один hot-key
    if now_in_burst_window() and random.random() < BURST_SAME_KEY_RATIO:
        return "hot-1"
    return hot_key() if random.random() < HOT_WRITE_RATIO else normal_key()

def choose_read_key():
    return hot_key() if random.random() < HOT_READ_RATIO else normal_key()

def choose_node():
    return random.choice(NODE_URLS)

def choose_operation():
    total = PUT_WEIGHT + REMOVE_WEIGHT + GET_WEIGHT + EXISTS_WEIGHT
    x = random.randint(1, total)
    if x <= PUT_WEIGHT:
        return "put"
    if x <= PUT_WEIGHT + REMOVE_WEIGHT:
        return "remove"
    if x <= PUT_WEIGHT + REMOVE_WEIGHT + GET_WEIGHT:
        return "get"
    return "exists"

class CrdtUser(HttpUser):
    wait_time = constant_pacing(1.0)
    host = NODE_URLS[0]

    @task
    def mixed(self):
        op = choose_operation()
        node = choose_node()

        if op == "put":
            self.do_put(node)
        elif op == "remove":
            self.do_remove(node)
        elif op == "get":
            self.do_get(node)
        else:
            self.do_exists(node)

    def do_put(self, node):
        key = choose_write_key()
        value = f"v-{int(time.time_ns())}-{random.randint(1, 100000)}"

        with self.client.post(
            f"{node}/api/crdt/tree/insert",
            json={"key": key, "value": value},
            name="PUT crdt insert",
            catch_response=True,
        ) as resp:
            if resp.status_code in (200, 201, 202, 204):
                resp.success()
            else:
                stats["http_errors"] += 1
                resp.failure(f"status={resp.status_code}")

    def do_remove(self, node):
        key = choose_write_key()

        with self.client.delete(
            f"{node}/api/crdt/tree/{key}",
            name="DELETE crdt key",
            catch_response=True,
        ) as resp:
            if resp.status_code in (200, 202, 204):
                resp.success()
            else:
                stats["http_errors"] += 1
                resp.failure(f"status={resp.status_code}")

    def do_get(self, node):
        key = choose_read_key()

        with self.client.get(
            f"{node}/api/crdt/tree/{key}",
            name="GET crdt key",
            catch_response=True,
        ) as resp:
            if resp.status_code != 200:
                if resp.status_code == 404:
                    stats["absent_reads"] += 1
                    resp.success()
                    return
                stats["http_errors"] += 1
                resp.failure(f"status={resp.status_code}")
                return

            try:
                body = resp.json()
                if body.get("conflict") is True:
                    stats["conflict_reads"] += 1
                else:
                    stats["normal_reads"] += 1
                resp.success()
            except Exception as exc:
                stats["http_errors"] += 1
                resp.failure(f"json error: {exc}")

    def do_exists(self, node):
        key = choose_read_key()

        with self.client.get(
            f"{node}/api/crdt/tree/{key}/exists",
            name="GET crdt exists",
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                stats["http_errors"] += 1
                resp.failure(f"status={resp.status_code}")

@events.quitting.add_listener
def _(environment, **kwargs):
    print("\n=== Conflict-oriented summary ===")
    print(f"conflict_reads: {stats['conflict_reads']}")
    print(f"normal_reads: {stats['normal_reads']}")
    print(f"absent_reads: {stats['absent_reads']}")
    print(f"http_errors: {stats['http_errors']}")
    print("=================================\n")