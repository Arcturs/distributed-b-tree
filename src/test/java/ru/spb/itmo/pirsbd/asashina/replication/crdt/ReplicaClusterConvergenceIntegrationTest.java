package ru.spb.itmo.pirsbd.asashina.replication.crdt;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.CrdtOperation;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.OperationType;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.AckRequest;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.GetResult;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.metrics.CrdtMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.properties.PeerRegistry;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.service.AckSender;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.service.CrdtBroadcaster;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.ReplicaActor;
import ru.spb.itmo.pirsbd.asashina.tree.BTree;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ReplicaClusterConvergenceIntegrationTest {

    private final List<TestNode> nodes = new ArrayList<>();

    @AfterEach
    void tearDown() {
        nodes.forEach(TestNode::close);
        nodes.clear();
    }

    @Test
    void replicasConvergeAfterReorderedDeliveryAndAckAllLocalWrites() {
        Map<String, String> peers = Map.of(
                "nodeA", "http://nodeA",
                "nodeB", "http://nodeB",
                "nodeC", "http://nodeC"
        );

        TestNode nodeA = new TestNode("nodeA", peers);
        TestNode nodeB = new TestNode("nodeB", peers);
        TestNode nodeC = new TestNode("nodeC", peers);
        nodes.addAll(List.of(nodeA, nodeB, nodeC));

        nodeA.setCluster(nodes);
        nodeB.setCluster(nodes);
        nodeC.setCluster(nodes);

        nodeA.start();
        nodeB.start();
        nodeC.start();

        nodeA.actor.localPut("k", "A1");
        nodeB.actor.localPut("k", "B1");
        nodeA.actor.localPut("chain", "v1");
        nodeA.actor.localRemove("chain");
        nodeC.actor.localPut("x", "C1");
        nodeA.actor.localRemove("x");

        CrdtOperation aKPut = nodeA.broadcaster.findByKeyAndType("k", OperationType.PUT);
        CrdtOperation aChainPut = nodeA.broadcaster.findByKeyAndType("chain", OperationType.PUT);
        CrdtOperation aChainRemove = nodeA.broadcaster.findByKeyAndType("chain", OperationType.REMOVE);
        CrdtOperation aXRemove = nodeA.broadcaster.findByKeyAndType("x", OperationType.REMOVE);
        CrdtOperation bKPut = nodeB.broadcaster.findByKeyAndType("k", OperationType.PUT);
        CrdtOperation cXPut = nodeC.broadcaster.findByKeyAndType("x", OperationType.PUT);

        // deliberately bad order
        deliverTo(nodeB, aChainRemove);
        deliverTo(nodeC, aChainRemove);
        deliverTo(nodeA, bKPut);
        deliverTo(nodeC, bKPut);
        deliverTo(nodeA, cXPut);
        deliverTo(nodeB, cXPut);
        deliverTo(nodeB, aChainPut);
        deliverTo(nodeC, aChainPut);
        deliverTo(nodeB, aKPut);
        deliverTo(nodeC, aKPut);
        deliverTo(nodeB, aXRemove);
        deliverTo(nodeC, aXRemove);

        waitUntil(() -> sameGet(nodeA, nodeB, "k") && sameGet(nodeA, nodeC, "k"), Duration.ofSeconds(3));
        waitUntil(() -> sameGet(nodeA, nodeB, "chain") && sameGet(nodeA, nodeC, "chain"), Duration.ofSeconds(3));
        waitUntil(() -> sameGet(nodeA, nodeB, "x") && sameGet(nodeA, nodeC, "x"), Duration.ofSeconds(3));
        waitUntil(() -> localWriteTimersFinished(nodeA) && localWriteTimersFinished(nodeB) && localWriteTimersFinished(nodeC), Duration.ofSeconds(3));

        GetResult kA = nodeA.actor.get("k");
        GetResult kB = nodeB.actor.get("k");
        GetResult kC = nodeC.actor.get("k");
        assertTrue(kA.conflict());
        assertEquals(Set.of("A1", "B1"), Set.copyOf(kA.values()));
        assertEquals(kA, kB);
        assertEquals(kA, kC);

        GetResult chainA = nodeA.actor.get("chain");
        assertFalse(chainA.exists(), "Causal remove after put must win once both are delivered");
        assertEquals(chainA, nodeB.actor.get("chain"));
        assertEquals(chainA, nodeC.actor.get("chain"));

        GetResult xA = nodeA.actor.get("x");
        assertTrue(xA.exists(), "Concurrent put and remove use add-wins visibility");
        assertFalse(xA.conflict());
        assertEquals("C1", xA.value());
        assertEquals(xA, nodeB.actor.get("x"));
        assertEquals(xA, nodeC.actor.get("x"));

        assertEquals(2, successCount(nodeA.registry, "put"));
        assertEquals(2, successCount(nodeA.registry, "remove"));
        assertEquals(1, successCount(nodeB.registry, "put"));
        assertEquals(1, successCount(nodeC.registry, "put"));
    }

    private static void deliverTo(TestNode target, CrdtOperation operation) {
        target.actor.receiveRemote(operation);
    }

    private static boolean sameGet(TestNode left, TestNode right, String key) {
        return left.actor.get(key).equals(right.actor.get(key));
    }

    private static boolean localWriteTimersFinished(TestNode node) {
        long putCount = successCount(node.registry, "put");
        long removeCount = successCount(node.registry, "remove");
        return putCount + removeCount == node.broadcaster.sent.size();
    }

    private static long successCount(SimpleMeterRegistry registry, String operation) {
        Timer timer = registry.find("crdt.replication.e2e.latency")
                .tags("operation", operation, "result", "success")
                .timer();
        return timer == null ? 0 : timer.count();
    }

    private static void waitUntil(Check check, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (check.ok()) {
                return;
            }
            sleep(10);
        }
        fail("Condition was not satisfied before timeout");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted");
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }

    private static final class TestNode {
        private final String id;
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        private final CrdtMetrics metrics = new CrdtMetrics(registry);
        private final CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        private final RoutingAckSender ackSender = new RoutingAckSender();
        private final ReplicaActor actor;

        private TestNode(String id, Map<String, String> peers) {
            this.id = id;
            this.actor = new ReplicaActor(
                    id,
                    new BTree<>(3),
                    new PeerRegistry(id, peers),
                    broadcaster,
                    ackSender,
                    metrics
            );
        }

        private void setCluster(List<TestNode> cluster) {
            broadcaster.setCluster(cluster, id);
            ackSender.setCluster(cluster);
        }

        private void start() {
            actor.start();
        }

        private void close() {
            actor.close();
        }
    }

    private static final class CapturingBroadcaster implements CrdtBroadcaster {
        private final List<CrdtOperation> sent = new CopyOnWriteArrayList<>();
        private Map<String, TestNode> nodes = Map.of();
        private String selfId;

        private void setCluster(List<TestNode> cluster, String selfId) {
            Map<String, TestNode> byId = new HashMap<>();
            for (TestNode node : cluster) {
                byId.put(node.id, node);
            }
            this.nodes = Map.copyOf(byId);
            this.selfId = selfId;
        }

        @Override
        public boolean broadcast(CrdtOperation operation) {
            sent.add(operation);
            return true;
        }

        private CrdtOperation findByKeyAndType(String key, OperationType type) {
            return sent.stream()
                    .filter(op -> op.key().equals(key) && op.operationType() == type)
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static final class RoutingAckSender implements AckSender {
        private Map<String, TestNode> nodes = Map.of();
        private final List<AckRequest> sent = new CopyOnWriteArrayList<>();

        private void setCluster(List<TestNode> cluster) {
            Map<String, TestNode> byId = new HashMap<>();
            for (TestNode node : cluster) {
                byId.put(node.id, node);
            }
            this.nodes = Map.copyOf(byId);
        }

        @Override
        public boolean sendAck(String originReplicaId, AckRequest request) {
            sent.add(request);
            TestNode origin = nodes.get(originReplicaId);
            if (origin == null) {
                return false;
            }
            origin.metrics.onAckReceived(request);
            return true;
        }
    }
}
