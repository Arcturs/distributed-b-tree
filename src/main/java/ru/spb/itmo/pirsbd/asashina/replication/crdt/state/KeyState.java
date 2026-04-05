package ru.spb.itmo.pirsbd.asashina.replication.crdt.state;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record KeyState<V>(Set<VersionedValue<V>> frontier) {

    public KeyState() {
        this(Set.of());
    }

    public KeyState(Set<VersionedValue<V>> frontier) {
        this.frontier = Set.copyOf(frontier);
    }

    public KeyState<V> apply(VersionedValue<V> incoming) {
        Set<VersionedValue<V>> next = new HashSet<>();
        var incomingDominated = false;
        for (var current : frontier) {
            var relation = incoming.versionVector().relate(current.versionVector());
            switch (relation) {
                case AFTER -> {
                    // incoming causally supersedes current
                }
                case BEFORE, EQUAL -> {
                    incomingDominated = true;
                    next.add(current);
                }
                case CONCURRENT -> next.add(current);
            }
        }

        if (!incomingDominated) {
            next.add(incoming);
        }
        return new KeyState<>(next);
    }

    public List<V> visibleValues() {
        return frontier.stream()
                .filter(v -> !v.tombstone())
                .map(VersionedValue::value)
                .distinct()
                .toList();
    }

    public boolean exists() {
        return !visibleValues().isEmpty();
    }

    public boolean conflict() {
        return visibleValues().size() > 1;
    }

    public Optional<V> singleValue() {
        var values = visibleValues();
        return values.size() == 1
                ? Optional.of(values.getFirst())
                : Optional.empty();
    }

}
