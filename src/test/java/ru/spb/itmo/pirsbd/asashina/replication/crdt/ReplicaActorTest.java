package ru.spb.itmo.pirsbd.asashina.replication.crdt;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.CrdtOperation;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.command.PutOperation;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.AckRequest;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.GetResult;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.metrics.CrdtMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.properties.PeerRegistry;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.service.AckSender;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.service.CrdtBroadcaster;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.ReplicaActor;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.VersionVector;
import ru.spb.itmo.pirsbd.asashina.tree.BTree;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ReplicaActorTest {

    private ReplicaActor actor;

    @AfterEach
    void tearDown() {
        if (actor != null) {
            actor.close();
        }
    }

    @Test
    void outOfOrderRemoteOperationsAreBufferedAndThenApplied() {
        TestBroadcaster broadcaster = new TestBroadcaster();
        TestAckSender ackSender = new TestAckSender();

        actor = new ReplicaActor(
                "nodeB",
                new BTree<>(3),
                new PeerRegistry("nodeB", Map.of("nodeA", "http://nodeA", "nodeB", "http://nodeB")),
                broadcaster,
                ackSender,
                new CrdtMetrics(new SimpleMeterRegistry())
        );
        actor.start();

        PutOperation op2 = new PutOperation(
                "op2",
                "nodeA",
                2,
                new VersionVector(Map.of("nodeA", 2L)),
                "k",
                System.currentTimeMillis(),
                "v2"
        );

        PutOperation op1 = new PutOperation(
                "op1",
                "nodeA",
                1,
                new VersionVector(Map.of("nodeA", 1L)),
                "k",
                System.currentTimeMillis(),
                "v1"
        );

        actor.receiveRemote(op2);
        sleep(100);
        assertFalse(actor.get("k").exists(), "Second op must stay pending until first arrives");

        actor.receiveRemote(op1);
        waitUntil(() -> actor.get("k").exists(), Duration.ofSeconds(2));

        GetResult result = actor.get("k");
        assertTrue(result.exists());
        assertFalse(result.conflict());
    }

    @Test
    void concurrentRemotePutsAppearAsConflictInSnapshot() {
        TestBroadcaster broadcaster = new TestBroadcaster();
        TestAckSender ackSender = new TestAckSender();

        actor = new ReplicaActor(
                "nodeB",
                new BTree<>(3),
                new PeerRegistry("nodeB", Map.of("nodeA", "http://nodeA", "nodeB", "http://nodeB", "nodeC", "http://nodeC")),
                broadcaster,
                ackSender,
                new CrdtMetrics(new SimpleMeterRegistry())
        );
        actor.start();

        PutOperation fromA = new PutOperation(
                "opA1",
                "nodeA",
                1,
                new VersionVector(Map.of("nodeA", 1L)),
                "k",
                System.currentTimeMillis(),
                "vA"
        );

        PutOperation fromC = new PutOperation(
                "opC1",
                "nodeC",
                1,
                new VersionVector(Map.of("nodeC", 1L)),
                "k",
                System.currentTimeMillis(),
                "vC"
        );

        actor.receiveRemote(fromA);
        actor.receiveRemote(fromC);
        waitUntil(() -> actor.get("k").conflict(), Duration.ofSeconds(2));

        GetResult result = actor.get("k");
        assertTrue(result.exists());
        assertTrue(result.conflict());
        assertEquals(List.of("vA", "vC").stream().sorted().toList(), result.values().stream().sorted().toList());
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

    private static class TestBroadcaster implements CrdtBroadcaster {
        private final List<CrdtOperation> sent = new CopyOnWriteArrayList<>();

        @Override
        public boolean broadcast(CrdtOperation operation) {
            sent.add(operation);
            return true;
        }
    }

    private static class TestAckSender implements AckSender {
        private final List<AckRequest> acks = new CopyOnWriteArrayList<>();

        @Override
        public boolean sendAck(String originReplicaId, AckRequest request) {
            acks.add(request);
            return true;
        }
    }
}
