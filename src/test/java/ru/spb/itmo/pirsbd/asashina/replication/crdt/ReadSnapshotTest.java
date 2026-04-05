package ru.spb.itmo.pirsbd.asashina.replication.crdt;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.dto.GetResult;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.metrics.CrdtMetrics;
import ru.spb.itmo.pirsbd.asashina.replication.crdt.state.ReadSnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReadSnapshotTest {

    @Test
    void absentKeyReturnsAbsentResult() {
        ReadSnapshot snapshot = new ReadSnapshot(new CrdtMetrics(new SimpleMeterRegistry()));

        GetResult result = snapshot.get("missing");

        assertFalse(result.exists());
        assertFalse(result.conflict());
        assertNull(result.value());
        assertEquals(List.of(), result.values());
    }

    @Test
    void conflictKeyCounterTracksTransitions() {
        ReadSnapshot snapshot = new ReadSnapshot(new CrdtMetrics(new SimpleMeterRegistry()));

        snapshot.put("k", GetResult.single("k", "A"));
        assertEquals(0, snapshot.conflictKeys());

        snapshot.put("k", GetResult.conflict("k", List.of("A", "B")));
        assertEquals(1, snapshot.conflictKeys());

        snapshot.put("k", GetResult.conflict("k", List.of("A", "B", "C")));
        assertEquals(1, snapshot.conflictKeys());

        snapshot.put("k", GetResult.single("k", "B"));
        assertEquals(0, snapshot.conflictKeys());

        snapshot.put("k", GetResult.absent("k"));
        assertEquals(0, snapshot.conflictKeys());
    }
}
