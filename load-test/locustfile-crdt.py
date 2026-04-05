import os
import random
from locust import HttpUser, task, constant_pacing

NODE1 = "http://localhost:8080"
NODE2 = "http://localhost:8081"
NODE3 ="http://localhost:8082"
KEYSPACE = 300

def node_url():
    return random.choice([NODE1, NODE2, NODE3])

def random_key():
    return str(random.randint(1, KEYSPACE))

class CrdtUser(HttpUser):
    host = NODE1
    wait_time = constant_pacing(1.0)

    @task(30)
    def put_value(self):
        self.client.post(
            f"{node_url()}/api/crdt/tree/insert",
            json={"key": random_key(), "value": f"value-{random.randint(1, 1_000_000)}"},
            name="POST /api/crdt/tree/insert"
        )

    @task(15)
    def remove_value(self):
        self.client.delete(
            f"{node_url()}/api/crdt/tree/{random_key()}",
            name="DELETE /api/crdt/tree/{key}"
        )

    @task(35)
    def get_value(self):
        self.client.get(
            f"{node_url()}/api/crdt/tree/{random_key()}",
            name="GET /api/crdt/tree/{key}"
        )

    @task(20)
    def exists(self):
        self.client.get(
            f"{node_url()}/api/crdt/tree/{random_key()}/exists",
            name="GET /api/crdt/tree/{key}/exists"
        )
