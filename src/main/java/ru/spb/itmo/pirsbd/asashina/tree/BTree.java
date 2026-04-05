package ru.spb.itmo.pirsbd.asashina.tree;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class BTree<K extends Comparable<K>, V> {

    private final int degree;
    private Node<K, V> root;

    @Getter
    private int size;

    public BTree(int degree) {
        this.degree = degree;
        this.root = new Node<>(true);
        this.size = 0;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        this.root = new Node<>(true);
        this.size = 0;
    }

    public V select(K key) {
        return search(root, key);
    }

    public boolean contains(K key) {
        return search(root, key) != null;
    }

    private V search(Node<K, V> node, K key) {
        var i = 0;
        var entries = node.getEntries();
        while (i < entries.size() && key.compareTo(entries.get(i).key()) > 0) {
            i++;
        }

        if (i < entries.size() && key.compareTo(entries.get(i).key()) == 0) {
            return entries.get(i).value();
        }

        if (node.isLeaf()) {
            return null;
        }

        return search(node.getChild(i), key);
    }

    public void insert(K key, V value) {
        if (contains(key)) {
            updateValue(root, key, value);
            return;
        }

        if (root.getEntriesSize() == 2 * degree - 1) {
            var newRoot = new Node<K, V>(false);
            newRoot.addChild(root);
            splitChild(newRoot, 0);
            root = newRoot;
        }
        insertNonFull(root, key, value);
        size++;
    }

    private void updateValue(Node<K, V> node, K key, V value) {
        var i = 0;
        var entries = node.getEntries();
        while (i < entries.size() && key.compareTo(entries.get(i).key()) > 0) {
            i++;
        }

        if (i < entries.size() && key.compareTo(entries.get(i).key()) == 0) {
            entries.set(i, new Entry<>(key, value));
            return;
        }

        if (!node.isLeaf()) {
            updateValue(node.getChild(i), key, value);
        }
    }

    private void insertNonFull(Node<K, V> node, K key, V value) {
        var i = node.getEntriesSize() - 1;
        if (node.isLeaf()) {
            var newEntry = new Entry<>(key, value);
            while (i >= 0 && key.compareTo(node.getEntry(i).key()) < 0) {
                i--;
            }
            node.getEntries().add(i + 1, newEntry);
        } else {
            while (i >= 0 && key.compareTo(node.getEntry(i).key()) < 0) {
                i--;
            }
            i++;
            if (node.getChild(i).getEntriesSize() == 2 * degree - 1) {
                splitChild(node, i);
                if (key.compareTo(node.getEntry(i).key()) > 0) {
                    i++;
                }
            }
            insertNonFull(node.getChild(i), key, value);
        }
    }

    private void splitChild(Node<K, V> parent, int childIndex) {
        var child = parent.getChildren().get(childIndex);
        var newChild = new Node<K, V>(child.isLeaf());
        for (var i = 0; i < degree - 1; i++) {
            newChild.getEntries().add(child.removeEntry(degree));
        }

        if (!child.isLeaf()) {
            for (var i = 0; i < degree; i++) {
                newChild.addChild(child.getChildren().remove(degree));
            }
        }

        var middleEntry = child.removeEntry(degree - 1);
        parent.getEntries().add(childIndex, middleEntry);
        parent.addChild(childIndex + 1, newChild);
    }

    public boolean remove(K key) {
        if (root.getEntries().isEmpty()) {
            return false;
        }

        if (!contains(key)) {
            return false;
        }
        var removed = remove(root, key);
        if (removed) {
            size--;
        }

        if (root.getEntries().isEmpty() && !root.isLeaf()) {
            root = root.getChildren().getFirst();
        }
        return removed;
    }

    private boolean remove(Node<K, V> node, K key) {
        var index = findKeyIndex(node, key);
        if (index < node.getEntriesSize() && key.compareTo(node.getEntry(index).key()) == 0) {
            if (node.isLeaf()) {
                removeFromLeaf(node, index);
            } else {
                removeFromInternal(node, index);
            }
            return true;
        }

        if (node.isLeaf()) {
            return false;
        }

        var flag = (index == node.getEntriesSize());
        if (node.getChild(index).getEntriesSize() < degree) {
            fill(node, index);
        }

        if (flag && index > node.getEntriesSize()) {
            return remove(node.getChild(index - 1), key);
        }
        return remove(node.getChild(index), key);
    }

    private int findKeyIndex(Node<K, V> node, K key) {
        var index = 0;
        while (index < node.getEntriesSize() && key.compareTo(node.getEntry(index).key()) > 0) {
            index++;
        }
        return index;
    }

    private void removeFromLeaf(Node<K, V> node, int index) {
        node.getEntries().remove(index);
    }

    private void removeFromInternal(Node<K, V> node, int index) {
        var key = node.getEntry(index).key();

        if (node.getChild(index).getEntriesSize() >= degree) {
            var predecessor = getPredecessor(node, index);
            node.getEntries().set(index, predecessor);
            remove(node.getChild(index), predecessor.key());
        } else if (node.getChild(index + 1).getEntriesSize() >= degree) {
            var successor = getSuccessor(node, index);
            node.getEntries().set(index, successor);
            remove(node.getChild(index + 1), successor.key());
        } else {
            merge(node, index);
            remove(node.getChild(index), key);
        }
    }

    private Entry<K, V> getPredecessor(Node<K, V> node, int index) {
        var current = node.getChild(index);
        while (!current.isLeaf()) {
            current = current.getChildren().getLast();
        }
        return current.getEntries().getLast();
    }

    private Entry<K, V> getSuccessor(Node<K, V> node, int index) {
        var current = node.getChild(index + 1);
        while (!current.isLeaf()) {
            current = current.getChildren().getFirst();
        }
        return current.getEntries().getFirst();
    }

    private void fill(Node<K, V> node, int index) {
        if (index != 0 && node.getChild(index - 1).getEntriesSize() >= degree) {
            borrowFromLeft(node, index);
        } else if (index != node.getEntriesSize() && node.getChild(index + 1).getEntriesSize() >= degree) {
            borrowFromRight(node, index);
        } else if (index != node.getEntriesSize()) {
            merge(node, index);
        } else {
            merge(node, index - 1);
        }
    }

    private void borrowFromLeft(Node<K, V> node, int index) {
        var child = node.getChild(index);
        var leftSibling = node.getChild(index - 1);

        child.getEntries().addFirst(node.getEntry(index - 1));
        node.getEntries().set(index - 1, leftSibling.getEntries().remove(leftSibling.getEntriesSize() - 1));

        if (!leftSibling.isLeaf()) {
            child.getChildren().addFirst(leftSibling.getChildren().removeLast());
        }
    }

    private void borrowFromRight(Node<K, V> node, int index) {
        var child = node.getChild(index);
        var rightSibling = node.getChild(index + 1);

        child.getEntries().add(node.getEntry(index));
        node.getEntries().set(index, rightSibling.getEntries().removeFirst());

        if (!rightSibling.isLeaf()) {
            child.getChildren().add(rightSibling.getChildren().removeFirst());
        }
    }

    private void merge(Node<K, V> node, int index) {
        var child = node.getChild(index);
        var sibling = node.getChild(index + 1);

        child.getEntries().add(node.getEntries().remove(index));
        child.getEntries().addAll(sibling.getEntries());

        if (!sibling.isLeaf()) {
            child.getChildren().addAll(sibling.getChildren());
        }

        node.getChildren().remove(index + 1);
    }

    public void printTree() {
        System.out.println("Tree (size: " + size + "):");
        printTree(root, 0);
    }

    private void printTree(Node<K, V> node, int level) {
        System.out.print("Level " + level + ": ");
        for (var entry : node.getEntries()) {
            System.out.print(entry.key() + "(" + entry.value() + ") ");
        }
        System.out.println();

        for (var child : node.getChildren()) {
            printTree(child, level + 1);
        }
    }

    public List<Entry<K, V>> getAllEntries() {
        List<Entry<K, V>> result = new ArrayList<>();
        inOrderTraversal(root, result);
        return result;
    }

    private void inOrderTraversal(Node<K, V> node, List<Entry<K, V>> result) {
        if (node == null) {
            return;
        }

        for (var i = 0; i < node.getEntriesSize(); i++) {
            if (!node.isLeaf()) {
                inOrderTraversal(node.getChild(i), result);
            }
            result.add(node.getEntry(i));
        }

        if (!node.isLeaf()) {
            inOrderTraversal(node.getChild(node.getEntriesSize()), result);
        }
    }

    public List<K> getAllKeys() {
        List<K> keys = new ArrayList<>();
        for (Entry<K, V> entry : getAllEntries()) {
            keys.add(entry.key());
        }
        return keys;
    }

    public List<V> getAllValues() {
        List<V> values = new ArrayList<>();
        for (Entry<K, V> entry : getAllEntries()) {
            values.add(entry.value());
        }
        return values;
    }

}


