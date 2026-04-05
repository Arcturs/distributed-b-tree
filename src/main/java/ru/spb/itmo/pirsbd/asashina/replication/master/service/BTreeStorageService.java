package ru.spb.itmo.pirsbd.asashina.replication.master.service;

import org.springframework.stereotype.Service;
import ru.spb.itmo.pirsbd.asashina.tree.BTree;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class BTreeStorageService {

    private final BTree<String, String> tree;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public BTreeStorageService(BTree<String, String> tree) {
        this.tree = tree;
    }

    public Optional<String> get(String key) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(tree.select(key));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean exists(String key) {
        lock.readLock().lock();
        try {
            return tree.contains(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void insert(String key, String value) {
        lock.writeLock().lock();
        try {
            tree.insert(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean remove(String key) {
        lock.writeLock().lock();
        try {
            return tree.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return tree.getSize();
        } finally {
            lock.readLock().unlock();
        }
    }

}
