package ru.spb.itmo.pirsbd.asashina.tree.cvrdt;

import ru.spb.itmo.pirsbd.asashina.tree.Entry;

public record SplitResult<K extends Comparable<K>, V>(
        Entry<K, V> middleEntry,
        CRDTNode<K, V> left,
        CRDTNode<K, V> right
) {

}
