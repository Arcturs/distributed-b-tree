package ru.spb.itmo.pirsbd.asashina.tree.cvrdt;

public record KeyMetadata(long insertCount, long deleteCount, long timestamp, String replicaId) {

    public boolean isPresent() {
        return insertCount > deleteCount;
    }

    public KeyMetadata merge(KeyMetadata other) {
        return new KeyMetadata(
                Math.max(this.insertCount, other.insertCount),
                Math.max(this.deleteCount, other.deleteCount),
                Math.max(this.timestamp, other.timestamp),
                this.timestamp != other.timestamp
                        ? (this.timestamp > other.timestamp ? this.replicaId : other.replicaId)
                        : (this.replicaId.compareTo(other.replicaId) > 0 ? this.replicaId : other.replicaId)
        );
    }

}
