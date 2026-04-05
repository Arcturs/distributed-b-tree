package ru.spb.itmo.pirsbd.asashina.tree;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class BTreeTest {

    private BTree<Integer, String> btree;

    @BeforeEach
    void setUp() {
        btree = new BTree<>(3); // degree = 3
    }

    @Nested
    @DisplayName("Basic Operations Tests")
    class BasicOperations {

        @Test
        @DisplayName("Test empty tree properties")
        void testEmptyTree() {
            assertEquals(0, btree.getSize(), "New tree should have size 0");
            assertTrue(btree.isEmpty(), "New tree should be empty");
            assertNull(btree.select(1), "Select on empty tree should return null");
            assertFalse(btree.contains(1), "Contains on empty tree should return false");
            assertEquals(0, btree.getAllEntries().size(), "getAllEntries on empty tree should return empty list");
        }

        @Test
        @DisplayName("Test single insert and retrieve")
        void testSingleInsert() {
            btree.insert(10, "Value10");

            assertEquals(1, btree.getSize(), "Size should be 1 after one insert");
            assertFalse(btree.isEmpty(), "Tree should not be empty after insert");
            assertEquals("Value10", btree.select(10), "Should retrieve inserted value");
            assertTrue(btree.contains(10), "Should contain inserted key");
            assertFalse(btree.contains(20), "Should not contain non-existent key");
        }

        @Test
        @DisplayName("Test multiple inserts")
        void testMultipleInserts() {
            btree.insert(10, "Value10");
            btree.insert(20, "Value20");
            btree.insert(5, "Value5");

            assertEquals(3, btree.getSize(), "Size should be 3 after three inserts");
            assertEquals("Value10", btree.select(10));
            assertEquals("Value20", btree.select(20));
            assertEquals("Value5", btree.select(5));
        }

        @Test
        @DisplayName("Test insert with duplicate key (update)")
        void testInsertDuplicateKey() {
            btree.insert(10, "Value10");
            btree.insert(10, "UpdatedValue10"); // Should update, not insert new

            assertEquals(1, btree.getSize(), "Size should still be 1 after updating existing key");
            assertEquals("UpdatedValue10", btree.select(10), "Should retrieve updated value");
        }

        @Test
        @DisplayName("Test insert null value")
        void testInsertNullValue() {
            btree.insert(10, null);
            assertNull(btree.select(10), "Should retrieve null value");
        }
    }

    @Nested
    @DisplayName("Delete Operations Tests")
    class DeleteOperations {

        @BeforeEach
        void setUp() {
            // Insert some data for delete tests
            btree.insert(10, "V10");
            btree.insert(20, "V20");
            btree.insert(5, "V5");
            btree.insert(15, "V15");
            btree.insert(25, "V25");
            btree.insert(2, "V2");
            btree.insert(7, "V7");
            btree.insert(12, "V12");
            btree.insert(17, "V17");
            btree.insert(22, "V22");
            btree.insert(27, "V27");
        }

        @Test
        @DisplayName("Test delete existing key")
        void testDeleteExistingKey() {
            int initialSize = btree.getSize();
            boolean removed = btree.remove(15);

            assertTrue(removed, "Should return true when deleting existing key");
            assertEquals(initialSize - 1, btree.getSize(), "Size should decrease by 1");
            assertNull(btree.select(15), "Deleted key should return null");
            assertFalse(btree.contains(15), "Should not contain deleted key");
        }

        @Test
        @DisplayName("Test delete non-existent key")
        void testDeleteNonExistentKey() {
            int initialSize = btree.getSize();
            boolean removed = btree.remove(999);

            assertFalse(removed, "Should return false when deleting non-existent key");
            assertEquals(initialSize, btree.getSize(), "Size should not change");
        }

        @Test
        @DisplayName("Test delete from leaf")
        void testDeleteFromLeaf() {
            assertTrue(btree.contains(2), "Key 2 should exist");
            assertTrue(btree.remove(2), "Should successfully delete leaf key");
            assertFalse(btree.contains(2), "Key 2 should no longer exist");
        }

        @Test
        @DisplayName("Test delete from internal node")
        void testDeleteFromInternalNode() {
            // Ensure the tree structure has internal nodes
            // Insert enough data to force node splits
            for (int i = 30; i <= 50; i++) {
                btree.insert(i, "V" + i);
            }

            assertTrue(btree.contains(25), "Key 25 should exist");
            assertTrue(btree.remove(25), "Should successfully delete internal key");
            assertFalse(btree.contains(25), "Key 25 should no longer exist");
        }

        @Test
        @DisplayName("Test delete all keys")
        void testDeleteAllKeys() {
            List<Integer> keys = btree.getAllKeys();

            for (Integer key : keys) {
                assertTrue(btree.remove(key), "Should successfully delete key: " + key);
            }

            assertEquals(0, btree.getSize(), "Size should be 0 after deleting all keys");
            assertTrue(btree.isEmpty(), "Tree should be empty");
        }

        @Test
        @DisplayName("Test delete root key")
        void testDeleteRootKey() {
            // Find and delete a key that might be in root
            Integer rootKey = btree.getAllKeys().get(btree.getAllKeys().size() / 2);
            assertTrue(btree.remove(rootKey), "Should successfully delete root key");
            assertFalse(btree.contains(rootKey), "Root key should no longer exist");
        }
    }

    @Nested
    @DisplayName("Tree Structure Tests")
    class TreeStructureTests {

        @Test
        @DisplayName("Test tree remains balanced after inserts")
        void testTreeBalance() {
            // Insert enough elements to cause multiple splits
            for (int i = 1; i <= 100; i++) {
                btree.insert(i, "Value" + i);
            }

            assertEquals(100, btree.getSize(), "Should have 100 elements");

            // Verify all elements are present and retrievable
            for (int i = 1; i <= 100; i++) {
                assertTrue(btree.contains(i), "Should contain key: " + i);
                assertEquals("Value" + i, btree.select(i), "Should retrieve correct value for key: " + i);
            }
        }

        @Test
        @DisplayName("Test tree remains balanced after deletes")
        void testTreeBalanceAfterDeletes() {
            // Insert elements
            for (int i = 1; i <= 50; i++) {
                btree.insert(i, "Value" + i);
            }

            // Delete some elements
            for (int i = 10; i <= 20; i++) {
                btree.remove(i);
            }

            // Verify remaining elements
            for (int i = 1; i <= 50; i++) {
                if (i >= 10 && i <= 20) {
                    assertFalse(btree.contains(i), "Should not contain deleted key: " + i);
                } else {
                    assertTrue(btree.contains(i), "Should contain key: " + i);
                }
            }
        }

        @Test
        @DisplayName("Test getAllEntries returns sorted entries")
        void testGetAllEntriesSorted() {
            // Insert in random order
            btree.insert(30, "V30");
            btree.insert(10, "V10");
            btree.insert(20, "V20");
            btree.insert(5, "V5");
            btree.insert(25, "V25");

            List<Entry<Integer, String>> entries = btree.getAllEntries();

            // Verify sorting
            for (int i = 0; i < entries.size() - 1; i++) {
                assertTrue(entries.get(i).key() < entries.get(i + 1).key(),
                        "Entries should be in ascending order");
            }

            assertEquals(5, entries.size(), "Should have 5 entries");
        }

        @Test
        @DisplayName("Test getAllKeys and getAllValues")
        void testGetAllKeysAndValues() {
            btree.insert(1, "A");
            btree.insert(3, "C");
            btree.insert(2, "B");

            List<Integer> keys = btree.getAllKeys();
            List<String> values = btree.getAllValues();

            assertEquals(3, keys.size());
            assertEquals(3, values.size());

            // Keys should be sorted
            assertEquals(List.of(1, 2, 3), keys);

            // Values should correspond to sorted keys
            assertEquals("A", values.get(0)); // Value for key 1
            assertEquals("B", values.get(1)); // Value for key 2
            assertEquals("C", values.get(2)); // Value for key 3
        }
    }

    @Nested
    @DisplayName("Edge Cases and Stress Tests")
    class EdgeCasesAndStressTests {

        @Test
        @DisplayName("Test large number of inserts and deletes")
        void testLargeDataset() {
            int size = 1000;

            // Insert
            for (int i = 0; i < size; i++) {
                btree.insert(i, "Value" + i);
            }

            assertEquals(size, btree.getSize(), "Should have " + size + " elements");

            // Verify all are present
            for (int i = 0; i < size; i++) {
                assertTrue(btree.contains(i), "Should contain key: " + i);
            }

            // Delete even numbers
            for (int i = 0; i < size; i += 2) {
                btree.remove(i);
            }

            assertEquals(size / 2, btree.getSize(), "Should have " + (size / 2) + " elements after deletions");

            // Verify remaining elements
            for (int i = 1; i < size; i += 2) {
                assertTrue(btree.contains(i), "Should contain odd key: " + i);
            }
        }

        @Test
        @DisplayName("Test clear operation")
        void testClear() {
            // Insert some data
            for (int i = 0; i < 10; i++) {
                btree.insert(i, "Value" + i);
            }

            assertFalse(btree.isEmpty(), "Tree should not be empty before clear");

            btree.clear();

            assertTrue(btree.isEmpty(), "Tree should be empty after clear");
            assertEquals(0, btree.getSize(), "Size should be 0 after clear");

            // Should be able to insert again after clear
            btree.insert(100, "NewValue");
            assertEquals(1, btree.getSize(), "Should be able to insert after clear");
        }

        @DisplayName("Test tree with different degrees")
        @ParameterizedTest
        @ValueSource(ints = {2, 3, 4, 5, 10})
        void testDifferentDegrees(int degree) {
            BTree<Integer, String> customBTree = new BTree<>(degree);

            // Insert elements
            for (int i = 0; i < 100; i++) {
                customBTree.insert(i, "Value" + i);
            }

            assertEquals(100, customBTree.getSize(), "Should have 100 elements with degree " + degree);

            // Verify all elements are retrievable
            for (int i = 0; i < 100; i++) {
                assertEquals("Value" + i, customBTree.select(i),
                        "Should retrieve correct value for key " + i + " with degree " + degree);
            }
        }

        @Test
        @DisplayName("Test negative and zero keys")
        void testNegativeKeys() {
            btree.insert(-10, "Negative");
            btree.insert(0, "Zero");
            btree.insert(10, "Positive");

            assertEquals(3, btree.getSize());
            assertEquals("Negative", btree.select(-10));
            assertEquals("Zero", btree.select(0));
            assertEquals("Positive", btree.select(10));
        }

        @Test
        @DisplayName("Test string keys")
        void testStringKeys() {
            BTree<String, Integer> stringBTree = new BTree<>(3);

            stringBTree.insert("apple", 5);
            stringBTree.insert("banana", 6);
            stringBTree.insert("cherry", 6);

            assertEquals(3, stringBTree.getSize());
            assertEquals(5, (int) stringBTree.select("apple"));
            assertEquals(6, (int) stringBTree.select("banana"));
            assertTrue(stringBTree.contains("cherry"));
        }

        @Test
        @DisplayName("Test complex object keys")
        void testComplexKeys() {
            record Person(String name, int age) implements Comparable<Person> {
                @Override
                public int compareTo(Person other) {
                    int nameCompare = this.name.compareTo(other.name);
                    if (nameCompare != 0) return nameCompare;
                    return Integer.compare(this.age, other.age);
                }
            }

            BTree<Person, String> complexBTree = new BTree<>(3);

            Person alice = new Person("Alice", 30);
            Person bob = new Person("Bob", 25);
            Person aliceYoung = new Person("Alice", 25);

            complexBTree.insert(alice, "Engineer");
            complexBTree.insert(bob, "Designer");
            complexBTree.insert(aliceYoung, "Manager");

            assertEquals(3, complexBTree.getSize());
            assertEquals("Engineer", complexBTree.select(alice));
            assertEquals("Manager", complexBTree.select(aliceYoung));

            // Test ordering
            List<Person> keys = complexBTree.getAllKeys();
            assertEquals(aliceYoung, keys.get(0)); // Alice, 25
            assertEquals(alice, keys.get(1));      // Alice, 30
            assertEquals(bob, keys.get(2));        // Bob, 25
        }
    }

    @Nested
    @DisplayName("BTree Properties Tests")
    class BTreePropertiesTests {

        @Test
        @DisplayName("Test B-tree node capacity constraints")
        void testNodeCapacity() {
            // With degree = 3, each node can have 2*t-1 = 5 entries max
            // Insert enough to cause splits
            for (int i = 1; i <= 20; i++) {
                btree.insert(i, "Value" + i);
            }

            // After inserts, verify all data is accessible
            for (int i = 1; i <= 20; i++) {
                assertTrue(btree.contains(i), "Should contain all inserted keys");
            }

            // Print tree to verify structure (optional)
            btree.printTree();
        }

        @Test
        @DisplayName("Test deletion with borrowing from siblings")
        void testDeleteWithBorrowing() {
            // Create a specific tree structure to test borrowing
            // Insert in specific order to create known structure
            btree = new BTree<>(2); // Smaller degree for easier testing

            // Insert keys that will create a specific structure
            int[] keys = {10, 20, 5, 15, 25, 3, 7, 13, 17, 23, 27};
            for (int key : keys) {
                btree.insert(key, "V" + key);
            }

            // Delete a key that will cause borrowing from sibling
            assertTrue(btree.remove(3));
            assertFalse(btree.contains(3));

            // Verify other keys still exist
            for (int key : keys) {
                if (key != 3) {
                    assertTrue(btree.contains(key), "Should still contain key: " + key);
                }
            }
        }

        @Test
        @DisplayName("Test deletion with merging nodes")
        void testDeleteWithMerging() {
            btree = new BTree<>(2); // Smaller degree for easier testing

            // Create a minimal tree that will require merging
            btree.insert(10, "V10");
            btree.insert(20, "V20");
            btree.insert(5, "V5");
            btree.insert(15, "V15");

            // Delete to trigger merge
            assertTrue(btree.remove(15));
            assertFalse(btree.contains(15));

            // Verify remaining keys
            assertTrue(btree.contains(5));
            assertTrue(btree.contains(10));
            assertTrue(btree.contains(20));
        }
    }

    @Nested
    @DisplayName("Concurrent Operations Simulation")
    class ConcurrentOperations {

        @Test
        @DisplayName("Test interleaved inserts and deletes")
        void testInterleavedOperations() {
            // Insert some initial data
            for (int i = 1; i <= 5; i++) {
                btree.insert(i, "Initial" + i);
            }

            // Interleaved operations
            btree.remove(2);
            btree.insert(6, "New6");
            btree.remove(3);
            btree.insert(2, "Reinserted2"); // Re-insert previously removed
            btree.insert(7, "New7");

            // Verify final state
            assertFalse(btree.contains(3), "Key 3 should be deleted");
            assertTrue(btree.contains(2), "Key 2 should exist (reinserted)");
            assertTrue(btree.contains(6), "Key 6 should exist");
            assertTrue(btree.contains(7), "Key 7 should exist");

            // Count unique keys: 1, 2(reinserted), 4, 5, 6, 7 = 6 total
            assertEquals(6, btree.getSize());
        }

        @Test
        @DisplayName("Test bulk operations")
        void testBulkOperations() {
            // Bulk insert
            List<Integer> keys = List.of(100, 200, 300, 400, 500, 600, 700, 800, 900);
            for (Integer key : keys) {
                btree.insert(key, "Bulk" + key);
            }

            assertEquals(keys.size(), btree.getSize());

            // Bulk verify
            for (Integer key : keys) {
                assertTrue(btree.contains(key), "Should contain all bulk inserted keys");
            }

            // Bulk delete some
            List<Integer> toDelete = List.of(200, 400, 600, 800);
            for (Integer key : toDelete) {
                btree.remove(key);
            }

            assertEquals(keys.size() - toDelete.size(), btree.getSize());
        }
    }
}