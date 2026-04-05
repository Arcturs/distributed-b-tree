package ru.spb.itmo.pirsbd.asashina.tree.cvrdt;

import lombok.Getter;
import lombok.Setter;
import ru.spb.itmo.pirsbd.asashina.tree.Entry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CRDTNode<K extends Comparable<K>, V> {

    private final List<Entry<K, V>> entries;
    private final List<CRDTNode<K, V>> children;
    private final boolean isLeaf;
    private final Map<K, KeyMetadata> metadata;

    public CRDTNode(
            List<Entry<K, V>> entries,
            List<CRDTNode<K, V>> children,
            boolean isLeaf,
            Map<K, KeyMetadata> metadata
    ) {
        this.entries = entries;
        this.children = children;
        this.isLeaf = isLeaf;
        this.metadata = metadata;
    }

    public CRDTNode() {
        this.entries = new ArrayList<>();
        this.children = new ArrayList<>();
        this.isLeaf = true;
        this.metadata = new HashMap<>();
    }

    public int findEntryIndex(K key) {
        for (var i = 0; i < entries.size(); i++) {
            if (entries.get(i).key().compareTo(key) == 0) {
                return i;
            }
        }
        return -1;
    }

    public CRDTNode<K, V> updateMetadata(K key, KeyMetadata newMeta) {
        Map<K, KeyMetadata> newMetadata = new HashMap<>(metadata);
        var current = newMetadata.get(key);
        if (current == null) {
            newMetadata.put(key, newMeta);
        } else {
            newMetadata.put(key, current.merge(newMeta));
        }

        return new CRDTNode<>(entries, children, isLeaf, newMetadata);
    }

    public CRDTNode<K, V> insertEntry(Entry<K, V> entry, KeyMetadata meta) {
        List<Entry<K, V>> newEntries = new ArrayList<>(entries);
        Map<K, KeyMetadata> newMetadata = new HashMap<>(metadata);

        // find insertion point
        var i = 0;
        while (i < newEntries.size() && entry.key().compareTo(newEntries.get(i).key()) > 0) {
            i++;
        }

        var existingMeta = newMetadata.get(entry.key());
        KeyMetadata finalMeta;

        if (existingMeta != null) {
            // Re-insert: increment insertCount
            var newInsertCount = Math.max(existingMeta.insertCount(), meta.insertCount());
            if (meta.insertCount() > 0) {
                newInsertCount++;
            }
            finalMeta = new KeyMetadata(
                    newInsertCount,
                    Math.max(existingMeta.deleteCount(), meta.deleteCount()),
                    Math.max(existingMeta.timestamp(), meta.timestamp()),
                    meta.timestamp() > existingMeta.timestamp() ? meta.replicaId() : existingMeta.replicaId());
        } else {
            finalMeta = meta;
        }

        // insert or update
        if (i < newEntries.size() && entry.key().compareTo(newEntries.get(i).key()) == 0) {
            newEntries.set(i, entry);
        } else {
            newEntries.add(i, entry);
        }

        newMetadata.put(entry.key(), finalMeta);
        return new CRDTNode<>(newEntries, children, isLeaf, newMetadata);
    }

    public SplitResult<K, V> split(int degree) {
        // B-tree split: when node has 2t-1 keys, split into two nodes with t-1 keys each middle entry goes to parent
        if (entries.size() < 2 * degree - 1) {
            return null;
        }

        var mid = degree - 1;
        Entry<K, V> middleEntry = entries.get(mid);
        List<Entry<K, V>> leftEntries = new ArrayList<>(entries.subList(0, mid));
        Map<K, KeyMetadata> leftMetadata = extractMetadata(leftEntries);
        List<Entry<K, V>> rightEntries = new ArrayList<>(entries.subList(mid + 1, entries.size()));
        Map<K, KeyMetadata> rightMetadata = extractMetadata(rightEntries);

        CRDTNode<K, V> leftNode, rightNode;
        if (isLeaf) {
            leftNode = new CRDTNode<>(leftEntries, new ArrayList<>(), true, leftMetadata);
            rightNode = new CRDTNode<>(rightEntries, new ArrayList<>(), true, rightMetadata);
        } else {
            List<CRDTNode<K, V>> leftChildren = new ArrayList<>(children.subList(0, mid + 1));
            List<CRDTNode<K, V>> rightChildren = new ArrayList<>(children.subList(mid + 1, children.size()));
            leftNode = new CRDTNode<>(leftEntries, leftChildren, false, leftMetadata);
            rightNode = new CRDTNode<>(rightEntries, rightChildren, false, rightMetadata);
        }
        return new SplitResult<>(middleEntry, leftNode, rightNode);
    }

    private Map<K, KeyMetadata> extractMetadata(List<Entry<K, V>> entriesList) {
        Map<K, KeyMetadata> result = new HashMap<>();
        for (var entry : entriesList) {
            var meta = metadata.get(entry.key());
            if (meta != null) {
                result.put(entry.key(), meta);
            }
        }
        return result;
    }

    public CRDTNode<K, V> replaceChild(int index, CRDTNode<K, V> newChild) {
        List<CRDTNode<K, V>> newChildren = new ArrayList<>(children);
        newChildren.set(index, newChild);
        return new CRDTNode<>(entries, newChildren, isLeaf, metadata);
    }

    public int findChildIndex(K key) {
        var i = 0;
        while (i < entries.size() && key.compareTo(entries.get(i).key()) > 0) {
            i++;
        }
        return i;
    }

}

