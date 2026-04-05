package ru.spb.itmo.pirsbd.asashina.tree;

public record Entry<K extends Comparable<K>, V> (K key, V value) {

}
