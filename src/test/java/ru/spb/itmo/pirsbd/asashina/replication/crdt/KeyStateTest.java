package ru.spb.itmo.pirsbd.asashina.replication.crdt;

import org.junit.jupiter.api.Test;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.KeyState;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.VersionVector;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.VersionedValue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KeyStateTest {

    @Test
    void singlePutProducesSingleVisibleValue() {
        KeyState<String> state = new KeyState<>();
        VersionedValue<String> value = put("op1", "node1", 1, Map.of("node1", 1L), "A");

        KeyState<String> next = state.apply(value);

        assertTrue(next.exists());
        assertFalse(next.conflict());
        assertEquals("A", next.singleValue().orElseThrow());
        assertEquals(List.of("A"), next.visibleValues());
    }

    @Test
    void causallyNewerPutReplacesOlderPut() {
        KeyState<String> state = new KeyState<>();

        KeyState<String> next = state
                .apply(put("op1", "node1", 1, Map.of("node1", 1L), "A"))
                .apply(put("op2", "node1", 2, Map.of("node1", 2L), "B"));

        assertTrue(next.exists());
        assertFalse(next.conflict());
        assertEquals("B", next.singleValue().orElseThrow());
        assertEquals(List.of("B"), next.visibleValues());
    }

    @Test
    void concurrentPutsProduceConflict() {
        KeyState<String> state = new KeyState<>();

        KeyState<String> next = state
                .apply(put("op1", "node1", 1, Map.of("node1", 1L), "A"))
                .apply(put("op2", "node2", 1, Map.of("node2", 1L), "B"));

        assertTrue(next.exists());
        assertTrue(next.conflict());
        assertEquals(Set.of("A", "B"), Set.copyOf(next.visibleValues()));
    }

    @Test
    void causalRemoveHidesEarlierPut() {
        KeyState<String> state = new KeyState<>();

        KeyState<String> next = state
                .apply(put("op1", "node1", 1, Map.of("node1", 1L), "A"))
                .apply(remove("op2", "node1", 2, Map.of("node1", 2L)));

        assertFalse(next.exists());
        assertFalse(next.conflict());
        assertTrue(next.visibleValues().isEmpty());
    }

    @Test
    void concurrentPutAndRemoveUseAddWinsVisibility() {
        KeyState<String> state = new KeyState<>();

        KeyState<String> next = state
                .apply(put("op1", "node1", 1, Map.of("node1", 1L), "A"))
                .apply(remove("op2", "node2", 1, Map.of("node2", 1L)));

        assertTrue(next.exists());
        assertFalse(next.conflict());
        assertEquals("A", next.singleValue().orElseThrow());
    }

    @Test
    void causallyNewerRemoveClearsAllConcurrentVisibleValuesItDominates() {
        KeyState<String> state = new KeyState<>();

        KeyState<String> conflicted = state
                .apply(put("op1", "node1", 1, Map.of("node1", 1L), "A"))
                .apply(put("op2", "node2", 1, Map.of("node2", 1L), "B"));

        KeyState<String> next = conflicted.apply(remove(
                "op3",
                "node3",
                1,
                Map.of("node1", 1L, "node2", 1L, "node3", 1L)
        ));

        assertFalse(next.exists());
        assertFalse(next.conflict());
        assertTrue(next.visibleValues().isEmpty());
    }

    @Test
    void equalVersionIsIgnoredAndDoesNotDuplicateValue() {
        VersionedValue<String> put = put("op1", "node1", 1, Map.of("node1", 1L), "A");

        KeyState<String> next = new KeyState<String>()
                .apply(put)
                .apply(put);

        assertTrue(next.exists());
        assertFalse(next.conflict());
        assertEquals(List.of("A"), next.visibleValues());
        assertEquals(1, next.frontier().size());
    }

    private static VersionedValue<String> put(String operationId, String originReplica, long originCounter,
                                              Map<String, Long> vv, String value) {
        return new VersionedValue<>(
                operationId,
                originReplica,
                originCounter,
                new VersionVector(vv),
                value,
                false
        );
    }

    private static VersionedValue<String> remove(String operationId, String originReplica, long originCounter,
                                                 Map<String, Long> vv) {
        return new VersionedValue<>(
                operationId,
                originReplica,
                originCounter,
                new VersionVector(vv),
                null,
                true
        );
    }
}
