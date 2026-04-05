package ru.spb.itmo.pirsbd.asashina.replication.crdt;

import org.junit.jupiter.api.Test;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.VersionVector;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionVectorTest {

    @Test
    void relateReturnsEqualForSameEntries() {
        VersionVector left = new VersionVector(Map.of("node1", 2L, "node2", 1L));
        VersionVector right = new VersionVector(Map.of("node1", 2L, "node2", 1L));

        assertEquals(VersionVector.Relation.EQUAL, left.relate(right));
        assertEquals(VersionVector.Relation.EQUAL, right.relate(left));
    }

    @Test
    void relateReturnsBeforeAndAfterForComparableVectors() {
        VersionVector older = new VersionVector(Map.of("node1", 1L, "node2", 2L));
        VersionVector newer = new VersionVector(Map.of("node1", 2L, "node2", 2L));

        assertEquals(VersionVector.Relation.BEFORE, older.relate(newer));
        assertEquals(VersionVector.Relation.AFTER, newer.relate(older));
    }

    @Test
    void relateReturnsConcurrentForCrossingVectors() {
        VersionVector left = new VersionVector(Map.of("node1", 2L, "node2", 0L));
        VersionVector right = new VersionVector(Map.of("node1", 1L, "node2", 1L));

        assertEquals(VersionVector.Relation.CONCURRENT, left.relate(right));
        assertEquals(VersionVector.Relation.CONCURRENT, right.relate(left));
    }

    @Test
    void missingEntriesAreTreatedAsZero() {
        VersionVector left = new VersionVector(Map.of("node1", 1L));
        VersionVector right = new VersionVector(Map.of("node1", 1L, "node2", 1L));

        assertEquals(VersionVector.Relation.BEFORE, left.relate(right));
        assertEquals(VersionVector.Relation.AFTER, right.relate(left));
    }
}
