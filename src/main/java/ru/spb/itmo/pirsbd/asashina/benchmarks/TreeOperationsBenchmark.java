package ru.spb.itmo.pirsbd.asashina.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import ru.spb.itmo.pirsbd.asashina.tree.BTree;
import ru.spb.itmo.pirsbd.asashina.tree.cvrdt.CRDTBTree;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Fork(2)
@Threads(1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.All)
@Warmup(iterations = 3, time = 1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1)
public class TreeOperationsBenchmark {

    private static final Random RANDOM = new Random(42);

    private BTree<Integer, Integer> btree;
    private CRDTBTree<Integer, Integer> crdtBTree;
    private int[] insertedKeys;

    @Setup(Level.Trial)
    public void setup() {
        btree = new BTree<>(3);
        crdtBTree = new CRDTBTree<>(3, "1");
        insertedKeys = new int[1000];
        for (int i = 0; i < 1_000; i++) {
            int key = RANDOM.nextInt(100_000);
            btree.insert(key, RANDOM.nextInt(1_000));
            crdtBTree.insert(key, RANDOM.nextInt(1_000));
            insertedKeys[i] = key;
        }
    }

    @Benchmark
    public void btreeInsert(Blackhole bh) {
        var key = RANDOM.nextInt(100_000);
        var value = RANDOM.nextInt(1_000);
        btree.insert(key, value);
        bh.consume(key);
    }

    @Benchmark
    public void crdtBtreeInsert(Blackhole bh) {
        var key = RANDOM.nextInt(100_000);
        var value = RANDOM.nextInt(1_000);
        crdtBTree.insert(key, value);
        bh.consume(key);
    }

    @Benchmark
    public void btreeContains(Blackhole bh) {
        var key = insertedKeys[RANDOM.nextInt(insertedKeys.length)];
        var result = btree.contains(key);
        bh.consume(result);
    }

    @Benchmark
    public void crdtBtreeContains(Blackhole bh) {
        var key = insertedKeys[RANDOM.nextInt(100)];
        var result = crdtBTree.contains(key);
        bh.consume(result);
    }

    @Benchmark
    public void btreeDelete(Blackhole bh) {
        var key = insertedKeys[RANDOM.nextInt(100)];
        var result = btree.remove(key);
        bh.consume(result);
    }

    @Benchmark
    public void crdtBtreeDelete(Blackhole bh) {
        var key = insertedKeys[RANDOM.nextInt(insertedKeys.length)];
        var result = crdtBTree.remove(key);
        bh.consume(result);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TreeOperationsBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

}
