package ru.spb.itmo.pirsbd.asashina.tree.cvrdt;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.spb.itmo.pirsbd.asashina.tree.Entry;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CRDTBTreeTest {

    private CRDTBTree<Integer, String> tree;
    private static final String REPLICA_ID = "TEST_REPLICA";

    @BeforeEach
    void setUp() {
        tree = new CRDTBTree<>(3, REPLICA_ID);
    }

    @Nested
    @DisplayName("KeyMetadata Tests")
    class KeyMetadataTests {

        @Test
        @DisplayName("KeyMetadata merge should take max counters")
        void testKeyMetadataMerge() {
            KeyMetadata meta1 = new KeyMetadata(2, 1, 100, "A");
            KeyMetadata meta2 = new KeyMetadata(3, 0, 200, "B");

            KeyMetadata merged = meta1.merge(meta2);

            assertEquals(3, merged.insertCount());
            assertEquals(1, merged.deleteCount());
            assertEquals(200, merged.timestamp());
            assertEquals("B", merged.replicaId()); // Higher timestamp wins
        }

        @Test
        @DisplayName("isPresent should return true when insertCount > deleteCount")
        void testIsPresent() {
            KeyMetadata present = new KeyMetadata(2, 1, 100, REPLICA_ID);
            KeyMetadata deleted = new KeyMetadata(2, 2, 100, REPLICA_ID);
            KeyMetadata neverInserted = new KeyMetadata(0, 0, 100, REPLICA_ID);

            assertTrue(present.isPresent());
            assertFalse(deleted.isPresent());
            assertFalse(neverInserted.isPresent());
        }

        @Test
        @DisplayName("Merge should be commutative")
        void testMergeCommutativity() {
            KeyMetadata meta1 = new KeyMetadata(2, 1, 150, "A");
            KeyMetadata meta2 = new KeyMetadata(1, 0, 100, "B");

            KeyMetadata merge1 = meta1.merge(meta2);
            KeyMetadata merge2 = meta2.merge(meta1);

            assertEquals(merge1.insertCount(), merge2.insertCount());
            assertEquals(merge1.deleteCount(), merge2.deleteCount());
            assertEquals(merge1.timestamp(), merge2.timestamp());
        }

        @Test
        @DisplayName("Merge should be idempotent")
        void testMergeIdempotence() {
            KeyMetadata meta = new KeyMetadata(2, 1, 100, "A");
            KeyMetadata merged = meta.merge(meta);

            assertEquals(meta.insertCount(), merged.insertCount());
            assertEquals(meta.deleteCount(), merged.deleteCount());
            assertEquals(meta.timestamp(), merged.timestamp());
            assertEquals(meta.replicaId(), merged.replicaId());
        }
    }

    @Nested
    @DisplayName("CRDTNode Tests")
    class CRDTNodeTests {

        @Test
        @DisplayName("Empty node should contain no keys")
        void testEmptyNode() {
            CRDTNode<Integer, String> node = new CRDTNode<>();

            assertTrue(node.isLeaf());
            assertTrue(node.getEntries().isEmpty());
            assertTrue(node.getChildren().isEmpty());
            assertTrue(node.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("insertEntry should add entry in sorted order")
        void testInsertEntry() {
            CRDTNode<Integer, String> node = new CRDTNode<>();
            KeyMetadata meta = new KeyMetadata(1, 0, 100, REPLICA_ID);

            node = node.insertEntry(new Entry<>(3, "C"), meta);
            node = node.insertEntry(new Entry<>(1, "A"), meta);
            node = node.insertEntry(new Entry<>(2, "B"), meta);

            List<Entry<Integer, String>> entries = node.getEntries();
            assertEquals(3, entries.size());
            assertEquals(1, entries.get(0).key());
            assertEquals(2, entries.get(1).key());
            assertEquals(3, entries.get(2).key());
            assertEquals("A", entries.get(0).value());
            assertEquals("B", entries.get(1).value());
            assertEquals("C", entries.get(2).value());
        }

        @Test
        @DisplayName("insertEntry should update existing key")
        void testInsertEntryUpdate() {
            CRDTNode<Integer, String> node = new CRDTNode<>();
            KeyMetadata meta1 = new KeyMetadata(1, 0, 100, REPLICA_ID);
            KeyMetadata meta2 = new KeyMetadata(2, 0, 200, REPLICA_ID);

            node = node.insertEntry(new Entry<>(1, "Original"), meta1);
            node = node.insertEntry(new Entry<>(1, "Updated"), meta2);

            assertEquals(1, node.getEntries().size());
            assertEquals("Updated", node.getEntries().get(0).value());

            KeyMetadata storedMeta = node.getMetadata().get(1);
            assertNotNull(storedMeta);
            assertEquals(3, storedMeta.insertCount());
            assertEquals(200, storedMeta.timestamp());
        }

        @Test
        @DisplayName("split should create balanced nodes when full")
        void testSplit() {
            CRDTNode<Integer, String> node = new CRDTNode<>();
            KeyMetadata meta = new KeyMetadata(1, 0, 100, REPLICA_ID);

            // Fill node to capacity (for degree=2, max entries=3)
            for (int i = 1; i <= 5; i++) {
                node = node.insertEntry(new Entry<>(i, "Value" + i), meta);
            }

            // With degree=2, 5 entries should trigger split
            SplitResult<Integer, String> split = node.split(2);
            assertNotNull(split);

            // Middle entry should be at position 2 (0-indexed)
            assertEquals(2, split.middleEntry().key());

            // Left node should have entries 1,2
            assertEquals(1, split.left().getEntries().size());
            assertEquals(1, split.left().getEntries().get(0).key());

            // Right node should have entries 4,5
            assertEquals(3, split.right().getEntries().size());
            assertEquals(3, split.right().getEntries().get(0).key());
            assertEquals(4, split.right().getEntries().get(1).key());
            assertEquals(5, split.right().getEntries().get(2).key());
        }

        @Test
        @DisplayName("updateMetadata should merge with existing metadata")
        void testUpdateMetadata() {
            CRDTNode<Integer, String> node = new CRDTNode<>();
            KeyMetadata meta1 = new KeyMetadata(1, 0, 100, "A");
            KeyMetadata meta2 = new KeyMetadata(2, 1, 200, "B");

            node = node.insertEntry(new Entry<>(1, "Value"), meta1);
            node = node.updateMetadata(1, meta2);

            KeyMetadata result = node.getMetadata().get(1);
            assertEquals(2, result.insertCount());
            assertEquals(1, result.deleteCount());
            assertEquals(200, result.timestamp());
            assertEquals("B", result.replicaId());
        }
    }

    @Nested
    @DisplayName("CRDTBTree Basic Operations")
    class BasicOperationsTests {

        @Test
        @DisplayName("New tree should be empty")
        void testEmptyTree() {
            assertEquals(0, tree.size());
            assertNull(tree.select(1));
            assertFalse(tree.contains(1));
            assertTrue(tree.getAllEntries().isEmpty());
        }

        @Test
        @DisplayName("Insert should add entries")
        void testInsert() {
            tree.insert(1, "Value1");
            tree.insert(2, "Value2");
            tree.insert(3, "Value3");

            assertEquals(3, tree.size());
            assertEquals("Value1", tree.select(1));
            assertEquals("Value2", tree.select(2));
            assertEquals("Value3", tree.select(3));
            assertTrue(tree.contains(1));
            assertFalse(tree.contains(4));
        }

        @Test
        @DisplayName("Remove should mark entry as deleted")
        void testRemove() {
            tree.insert(1, "Value1");
            tree.insert(2, "Value2");

            assertTrue(tree.remove(1));
            assertFalse(tree.contains(1));
            assertNull(tree.select(1));
            assertTrue(tree.contains(2)); // Other key still present

            // Size should reflect only present entries
            assertEquals(1, tree.size());
        }

        @Test
        @DisplayName("Remove non-existent key should return false")
        void testRemoveNonExistent() {
            assertFalse(tree.remove(999));
            assertEquals(0, tree.size());
        }

        @Test
        @DisplayName("Remove and re-insert should work")
        void testRemoveAndReinsert() {
            tree.insert(1, "Original");
            assertTrue(tree.contains(1));

            // Remove should return true and actually remove
            assertTrue(tree.remove(1));
            assertFalse(tree.contains(1));
            assertEquals(0, tree.size());

            // Re-insert should work
            tree.insert(1, "Reinserted");
            assertTrue(tree.contains(1));
            assertEquals("Reinserted", tree.select(1));
            assertEquals(1, tree.size());
        }

        @Test
        @DisplayName("Select should return null for non-existent key")
        void testSelectNonExistent() {
            assertNull(tree.select(999));
        }

        @ParameterizedTest
        @ValueSource(ints = {2, 3, 4, 5})
        @DisplayName("Tree should work with different degrees")
        void testDifferentDegrees(int degree) {
            CRDTBTree<Integer, String> customTree = new CRDTBTree<>(degree, REPLICA_ID);

            for (int i = 1; i <= 50; i++) {
                customTree.insert(i, "V" + i);
            }

            assertEquals(50, customTree.size());

            for (int i = 1; i <= 50; i++) {
                assertEquals("V" + i, customTree.select(i));
            }
            customTree.printTree();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Stress Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Tree should handle large number of entries")
        void testLargeDataset() {
            int size = 1000;

            for (int i = 0; i < size; i++) {
                tree.insert(i, "Value" + i);
            }

            assertEquals(size, tree.size());

            // Random sampling
            var random = new Random(42);
            for (int i = 0; i < 100; i++) {
                int key = random.nextInt(size);
                assertEquals("Value" + key, tree.select(key));
            }

            // Delete half
            for (int i = 0; i < size; i += 2) {
                tree.remove(i);
            }

            assertEquals(size / 2, tree.size());

            // Reinsert some
            for (int i = 0; i < size; i += 4) {
                tree.insert(i, "Reinserted" + i);
            }
        }

        @Test
        @DisplayName("Tree should handle interleaved inserts and deletes")
        void testInterleavedOperations() {
            // Insert some keys
            tree.insert(1, "A");
            tree.insert(2, "B");
            tree.insert(3, "C");

            // Delete one
            tree.remove(2);

            // Insert more
            tree.insert(4, "D");
            tree.insert(5, "E");

            // Delete another
            tree.remove(1);

            // Reinsert deleted
            tree.insert(2, "B2");

            assertEquals(4, tree.size()); // Keys 2,3,4,5
            assertNull(tree.select(1));
            assertEquals("B2", tree.select(2));
            assertEquals("C", tree.select(3));
            assertEquals("D", tree.select(4));
            assertEquals("E", tree.select(5));
        }

        @Test
        @DisplayName("Tree should maintain order after complex operations")
        void testOrdering() {
            // Insert in random order
            tree.insert(50, "V50");
            tree.insert(20, "V20");
            tree.insert(80, "V80");
            tree.insert(10, "V10");
            tree.insert(30, "V30");
            tree.insert(70, "V70");
            tree.insert(90, "V90");
            tree.insert(40, "V40");
            tree.insert(60, "V60");

            List<Entry<Integer, String>> entries = tree.getAllEntries();

            // Should be sorted by key
            for (int i = 0; i < entries.size() - 1; i++) {
                assertTrue(entries.get(i).key() < entries.get(i + 1).key());
            }

            assertEquals(9, entries.size());
        }

        @Test
        @DisplayName("Tree should handle string keys")
        void testStringKeys() {
            CRDTBTree<String, Integer> stringTree = new CRDTBTree<>(3, "STRING_REPLICA");

            stringTree.insert("apple", 5);
            stringTree.insert("banana", 6);
            stringTree.insert("cherry", 7);

            assertEquals(3, stringTree.size());
            assertEquals(5, (int) stringTree.select("apple"));
            assertTrue(stringTree.contains("banana"));

            stringTree.remove("banana");
            assertFalse(stringTree.contains("banana"));
            assertNull(stringTree.select("banana"));
        }

        @Test
        @DisplayName("Tree should work with custom comparable objects")
        void testCustomKeyType() {
            record Person(String name, int age) implements Comparable<Person> {
                @Override
                public int compareTo(Person other) {
                    int nameCompare = this.name.compareTo(other.name);
                    if (nameCompare != 0) return nameCompare;
                    return Integer.compare(this.age, other.age);
                }
            }

            CRDTBTree<Person, String> personTree = new CRDTBTree<>(3, "PERSON_REPLICA");

            Person alice = new Person("Alice", 30);
            Person bob = new Person("Bob", 25);
            Person aliceYoung = new Person("Alice", 25);

            personTree.insert(alice, "Engineer");
            personTree.insert(bob, "Designer");
            personTree.insert(aliceYoung, "Manager");

            assertEquals(3, personTree.size());
            assertEquals("Engineer", personTree.select(alice));
            assertEquals("Manager", personTree.select(aliceYoung));

            // getAllEntries should be sorted
            List<Entry<Person, String>> entries = personTree.getAllEntries();
            assertEquals(aliceYoung, entries.get(0).key()); // Alice, 25
            assertEquals(alice, entries.get(1).key());      // Alice, 30
            assertEquals(bob, entries.get(2).key());        // Bob, 25
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Test B-tree invariants after operations")
        void testBTreeInvariants() {
            // Test with degree 2
            CRDTBTree<Integer, String> tree = new CRDTBTree<>(2, "TEST");

            // Insert 20 items
            for (int i = 1; i <= 20; i++) {
                tree.insert(i, "Value" + i);
            }

            assertEquals(20, tree.size());

            // Verify all are present and retrievable
            for (int i = 1; i <= 20; i++) {
                assertTrue(tree.contains(i), "Missing key: " + i);
                assertEquals("Value" + i, tree.select(i));
            }

            // Delete every other item
            for (int i = 1; i <= 20; i += 2) {
                tree.remove(i);
            }

            assertEquals(10, tree.size());

            // Verify only even numbers remain
            for (int i = 1; i <= 20; i++) {
                if (i % 2 == 0) {
                    assertTrue(tree.contains(i), "Even key should exist: " + i);
                } else {
                    assertFalse(tree.contains(i), "Odd key should not exist: " + i);
                }
            }

            // Reinsert some deleted items
            for (int i = 1; i <= 20; i += 4) {
                tree.insert(i, "Reinserted" + i);
            }

            // Check final state
            for (int i = 1; i <= 20; i++) {
                if (i % 2 == 0) {
                    assertEquals("Value" + i, tree.select(i));
                } else if (i % 4 == 1) {
                    assertEquals("Reinserted" + i, tree.select(i));
                } else {
                    assertNull(tree.select(i));
                }
            }
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        @DisplayName("Performance: Insert 10,000 items")
        void testInsertPerformance() {
            CRDTBTree<Integer, String> tree = new CRDTBTree<>(4, "PERF_TEST");

            long start = System.currentTimeMillis();
            for (int i = 0; i < 10_000; i++) {
                tree.insert(i, "Value" + i);
            }
            long end = System.currentTimeMillis();

            System.out.printf("Inserted 10,000 items in %d ms%n", end - start);
            assertEquals(10_000, tree.size());
        }

        @Test
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        @DisplayName("Performance: Mixed operations")
        void testMixedOperationsPerformance() {
            CRDTBTree<Integer, String> tree = new CRDTBTree<>(4, "MIXED_TEST");

            long start = System.currentTimeMillis();

            // Insert batch
            for (int i = 0; i < 5_000; i++) {
                tree.insert(i, "Value" + i);
            }

            // Delete half
            for (int i = 0; i < 5_000; i += 2) {
                tree.remove(i);
            }

            // Reinsert some
            for (int i = 0; i < 5_000; i += 4) {
                tree.insert(i, "Reinserted" + i);
            }

            // Query
            for (int i = 0; i < 1_000; i++) {
                tree.contains(i);
            }

            long end = System.currentTimeMillis();

            System.out.printf("Mixed operations completed in %d ms%n", end - start);
            assertTrue(tree.size() > 0);
        }
    }
}
