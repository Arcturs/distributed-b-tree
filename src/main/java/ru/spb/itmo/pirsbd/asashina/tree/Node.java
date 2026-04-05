package ru.spb.itmo.pirsbd.asashina.tree;

import lombok.Getter;
import lombok.Setter;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Node<K extends Comparable<K>, V> {

    private final List<Entry<K, V>> entries;
    private final List<Node<K, V>> children;
    private boolean isLeaf;

    public Node(boolean isLeaf) {
        this.entries = new ArrayList<>();
        this.children = new ArrayList<>();
        this.isLeaf = isLeaf;
    }

    public void addChild(Node<K, V> node) {
        children.add(node);
    }

    public void addChild(int index, Node<K, V> node) {
        children.add(index, node);
    }

    public Entry<K, V> removeEntry(int index) {
        return entries.remove(index);
    }

    public Node<K, V> getChild(int index) {
        return children.get(index);
    }

    public Entry<K, V> getEntry(int index) {
        return entries.get(index);
    }

    public int getEntriesSize() {
        return entries.size();
    }

}

