package ru.spb.itmo.pirsbd.asashina.tree.cvrdt;

import ru.spb.itmo.pirsbd.asashina.tree.Entry;

import java.util.*;

public class CRDTBTree<K extends Comparable<K>, V> {

    private final int degree;
    private CRDTNode<K, V> root;
    private final String replicaId;
    private long currentTimestamp;

    public CRDTBTree(int degree, String replicaId) {
        this.degree = degree;
        this.replicaId = replicaId;
        this.currentTimestamp = System.nanoTime();
        this.root = new CRDTNode<>();
    }

    public void insert(K key, V value) {
        var timestamp = nextTimestamp();
        var existingMeta = findKeyMetadata(key);
        var insertCount = existingMeta != null ? existingMeta.insertCount() + 1 : 1;
        var deleteCount = existingMeta != null ? existingMeta.deleteCount() : 0;

        var meta = new KeyMetadata(insertCount, deleteCount, timestamp, replicaId);
        var result = insert(root, key, value, meta);

        if (result.newRoot != null) {
            root = result.newRoot;
        } else {
            root = result.node;
        }
    }

    private long nextTimestamp() {
        return ++currentTimestamp;
    }

    private KeyMetadata findKeyMetadata(K key) {
        return findKeyMetadata(root, key);
    }

    private KeyMetadata findKeyMetadata(CRDTNode<K, V> node, K key) {
        for (var i = 0; i < node.getEntries().size(); i++) {
            var entry = node.getEntries().get(i);
            var cmp = key.compareTo(entry.key());
            if (cmp == 0) {
                return node.getMetadata().get(key);
            }
            if (cmp < 0) {
                if (node.isLeaf()) {
                    return null;
                }
                return findKeyMetadata(node.getChildren().get(i), key);
            }
        }

        if (node.isLeaf()) {
            return null;
        }
        return findKeyMetadata(node.getChildren().get(node.getEntries().size()), key);
    }

    private InsertResult<K, V> insert(CRDTNode<K, V> node, K key, V value, KeyMetadata meta) {
        var existingIndex = node.findEntryIndex(key);
        if (existingIndex != -1) {
            var updatedNode = node.insertEntry(new Entry<>(key, value), meta);
            return new InsertResult<>(updatedNode, null);
        }

        if (node.isLeaf()) {
            var updatedNode = node.insertEntry(new Entry<>(key, value), meta);
            if (updatedNode.getEntries().size() <= 2 * degree - 1) {
                return new InsertResult<>(updatedNode, null);
            }

            var split = updatedNode.split(degree);
            var newRoot = new CRDTNode<>(
                    List.of(split.middleEntry()),
                    List.of(split.left(), split.right()),
                    false,
                    new HashMap<>());
            newRoot = newRoot.updateMetadata(split.middleEntry().key(), meta);
            return new InsertResult<>(null, newRoot);
        }

        var childIndex = node.findChildIndex(key);
        var child = node.getChildren().get(childIndex);
        InsertResult<K, V> childResult;

        if (child.getEntries().size() >= 2 * degree - 1) {
            var split = child.split(degree);
            var updatedNode = node.insertEntry(
                    split.middleEntry(),
                    child.getMetadata().get(split.middleEntry().key()));
            updatedNode = new CRDTNode<>(
                    updatedNode.getEntries(),
                    updatedNode.getChildren(),
                    false,
                    updatedNode.getMetadata()
            );

            var insertPos = childIndex;
            for (var i = 0; i < updatedNode.getEntries().size(); i++) {
                if (updatedNode.getEntries().get(i).key().compareTo(split.middleEntry().key()) == 0) {
                    insertPos = i;
                    break;
                }
            }

            List<CRDTNode<K, V>> newChildren = new ArrayList<>();
            for (var i = 0; i < updatedNode.getChildren().size(); i++) {
                if (i == childIndex) {
                    newChildren.add(split.left());
                    newChildren.add(split.right());
                } else {
                    newChildren.add(updatedNode.getChildren().get(i));
                }
            }
            updatedNode = new CRDTNode<>(
                    updatedNode.getEntries(),
                    newChildren,
                    false,
                    updatedNode.getMetadata()
            );

            if (key.compareTo(split.middleEntry().key()) > 0) {
                childIndex = insertPos + 1;
            } else {
                childIndex = insertPos;
            }
            child = updatedNode.getChildren().get(childIndex);
            childResult = insert(child, key, value, meta);
            updatedNode = updatedNode.replaceChild(childIndex, childResult.node);

            if (updatedNode.getEntries().size() <= 2 * degree - 1) {
                return new InsertResult<>(updatedNode, null);
            }
            var nodeSplit = updatedNode.split(degree);
            var newRoot = new CRDTNode<>(
                    List.of(nodeSplit.middleEntry()),
                    List.of(nodeSplit.left(), nodeSplit.right()),
                    false,
                    updatedNode.getMetadata()
            );
            return new InsertResult<>(null, newRoot);
        }

        childResult = insert(child, key, value, meta);
        if (childResult.newRoot != null) {
            var newChildRoot = childResult.newRoot;
            var updatedNode = node.insertEntry(
                    newChildRoot.getEntries().getFirst(),
                    newChildRoot.getMetadata().get(newChildRoot.getEntries().getFirst().key())
            );

            List<CRDTNode<K, V>> newChildren = new ArrayList<>();
            for (var i = 0; i < node.getChildren().size(); i++) {
                if (i == childIndex) {
                    newChildren.add(newChildRoot.getChildren().get(0));
                    newChildren.add(newChildRoot.getChildren().get(1));
                } else {
                    newChildren.add(node.getChildren().get(i));
                }
            }

            updatedNode = new CRDTNode<>(
                    updatedNode.getEntries(),
                    newChildren,
                    false,
                    updatedNode.getMetadata()
            );

            if (updatedNode.getEntries().size() <= 2 * degree - 1) {
                return new InsertResult<>(updatedNode, null);
            }

            var nodeSplit = updatedNode.split(degree);
            var newRoot = new CRDTNode<>(
                    List.of(nodeSplit.middleEntry()),
                    List.of(nodeSplit.left(), nodeSplit.right()),
                    false,
                    updatedNode.getMetadata()
            );
            return new InsertResult<>(null, newRoot);
        }

        var updatedNode = node.replaceChild(childIndex, childResult.node);
        return new InsertResult<>(updatedNode, null);
    }

    public boolean remove(K key) {
        if (!contains(key)) {
            return false;
        }

        var timestamp = nextTimestamp();
        var meta = new KeyMetadata(0, 1, timestamp, replicaId);
        root = remove(root, key, meta);
        return true;
    }

    private CRDTNode<K, V> remove(CRDTNode<K, V> node, K key, KeyMetadata meta) {
        var updatedNode = node.updateMetadata(key, meta);
        if (node.isLeaf()) {
            return updatedNode;
        }

        var childIndex = node.findChildIndex(key);
        var child = updatedNode.getChildren().get(childIndex);
        var updatedChild = remove(child, key, meta);
        return updatedNode.replaceChild(childIndex, updatedChild);
    }

    public V select(K key) {
        return select(root, key);
    }

    private V select(CRDTNode<K, V> node, K key) {
        var i = 0;
        while (i < node.getEntries().size() && key.compareTo(node.getEntries().get(i).key()) > 0) {
            i++;
        }

        if (i < node.getEntries().size() && key.compareTo(node.getEntries().get(i).key()) == 0) {
            var meta = node.getMetadata().get(key);
            if (meta != null && meta.isPresent()) {
                return node.getEntries().get(i).value();
            }
            return null;
        }

        if (node.isLeaf()) {
            return null;
        }

        return select(node.getChildren().get(i), key);
    }

    public boolean contains(K key) {
        return select(key) != null;
    }

    public List<Entry<K, V>> getAllEntries() {
        List<Entry<K, V>> result = new ArrayList<>();
        inOrderTraversal(root, result);
        return result;
    }

    private void inOrderTraversal(CRDTNode<K, V> node, List<Entry<K, V>> result) {
        if (node == null) {
            return;
        }

        if (node.isLeaf()) {
            for (Entry<K, V> entry : node.getEntries()) {
                KeyMetadata meta = node.getMetadata().get(entry.key());
                if (meta != null && meta.isPresent()) {
                    result.add(entry);
                }
            }
            return;
        }

        for (int i = 0; i < node.getEntries().size(); i++) {
            inOrderTraversal(node.getChildren().get(i), result);

            Entry<K, V> entry = node.getEntries().get(i);
            KeyMetadata meta = node.getMetadata().get(entry.key());
            if (meta != null && meta.isPresent()) {
                result.add(entry);
            }
        }
        inOrderTraversal(node.getChildren().get(node.getEntries().size()), result);
    }

    public int size() {
        return getAllEntries().size();
    }

    public void printTree() {
        printNode(root, 0);
    }

    private void printNode(CRDTNode<K, V> node, int level) {
        var indent = "  ".repeat(level);
        System.out.print(indent + "Level " + level + ": ");
        for (var entry : node.getEntries()) {
            System.out.print(entry.key() + "(" + entry.value() + ") ");
        }
        System.out.println();
        if (!node.isLeaf()) {
            for (var child : node.getChildren()) {
                printNode(child, level + 1);
            }
        }
    }

    private record InsertResult<K extends Comparable<K>, V>(
            CRDTNode<K, V> node,
            CRDTNode<K, V> newRoot
    ) {

    }

}

