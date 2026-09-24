import dev.escos.fork.EscoConfig;
import dev.escos.fork.EscoMetrics;
import dev.escos.fork.ai.DistanceOrder;
import dev.escos.fork.concurrent.ParallelWork;
import dev.escos.fork.entity.ActivationSnapshot;
import dev.escos.fork.entity.PotentialWork;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class KernelChecks {
    private record Value(int id, double distance) {}
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--config-only")) {
            System.out.println("workers=" + EscoConfig.WORKERS);
            return;
        }
        final Random random = new Random(2602);
        long valuesChecked = 0;
        for (int size : new int[] {0, 1, 2, 63, 64, 65, 1023, 1024, 1025, 2047, 2048, 2049, 4095, 4096, 4097, 8191, 8192, 8193, 16383, 16384, 16385, 50000, 65535, 65536, 65537}) {
            for (int mode = 0; mode < 7; ++mode) {
                ArrayList<Value> input = new ArrayList<>();
                for (int i = 0; i < size; ++i) {
                    double key = switch (mode) {
                        case 0 -> random.nextDouble();
                        case 1 -> i;
                        case 2 -> -i;
                        case 3 -> random.nextInt(7);
                        case 4 -> new double[] {-0.0, 0.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}[i % 5];
                        case 5 -> Double.longBitsToDouble(random.nextLong());
                        default -> 1;
                    };
                    input.add(new Value(i, key));
                }
                final ArrayList<Value> expected = new ArrayList<>(input);
                expected.sort(Comparator.comparingDouble(Value::distance));
                final Thread caller = Thread.currentThread();
                DistanceOrder.sort(input, v -> {
                    require(Thread.currentThread() == caller, "distance extractor escaped caller thread");
                    return v.distance;
                });
                require(input.equals(expected), "stable order differs: " + size + "/" + mode);
                valuesChecked += size;
            }
        }
        // Floating-point equality, immutable append caching, mutable recapture and size reuse.
        final double[][] points = new double[65537][4];
        for (int i = 0; i < points.length; ++i) {
            points[i] = new double[] {random.nextInt(), random.nextInt(), random.nextInt(), random.nextDouble() * 10 - 5};
        }
        final PotentialWork potential = new PotentialWork();
        final Thread potentialCaller = Thread.currentThread();
        long potentialValues = 0;
        for (int n : new int[] {0, 1, 1023, 1024, 1025, 4095, 4096, 4097, 16384, 65537, 13, 4096}) {
            for (int q = 0; q < 7; ++q) {
                final double x = q == 0 ? points[0][0] : (double)Integer.MAX_VALUE - q;
                final double y = q == 0 ? points[0][1] : Integer.MIN_VALUE;
                final double z = q == 0 ? points[0][2] : 72;
                if (q == 3) points[0][3] = Double.NaN;
                if (q == 4) points[0][3] = Double.NEGATIVE_INFINITY;
                if (q == 5) points[0][3] = Double.POSITIVE_INFINITY;
                if (q == 6) points[0][3] = -0.0;
                final double result = potential.sum(n, x, y, z, (index, target, offset) -> {
                    require(Thread.currentThread() == potentialCaller, "potential capture escaped caller");
                    System.arraycopy(points[index], 0, target, offset, 4);
                }, false);
                double expected = 0.0;
                for (int i = 0; i < n; ++i) {
                    final double dx = points[i][0] - x, dy = points[i][1] - y, dz = points[i][2] - z;
                    final double d = dx * dx + dy * dy + dz * dz;
                    expected += d == 0.0 ? Double.POSITIVE_INFINITY : points[i][3] / Math.sqrt(d);
                }
                require(Double.doubleToLongBits(result) == Double.doubleToLongBits(expected), "potential IEEE order/recapture n=" + n);
                potentialValues += n;
            }
            points[0][3] = 1.5;
        }
        final PotentialWork cached = new PotentialWork();
        AtomicInteger reads = new AtomicInteger();
        for (int n : new int[] {4096, 4096, 4097, 4097, 16384, 16384}) {
            final double actual = cached.sum(n, 0, 0, 0, (i, target, offset) -> {
                reads.incrementAndGet(); System.arraycopy(points[i], 0, target, offset, 4);
            }, true);
            final double expected = new PotentialWork().sum(n, 0, 0, 0,
                (i, target, offset) -> System.arraycopy(points[i], 0, target, offset, 4), false);
            require(Double.doubleToLongBits(actual) == Double.doubleToLongBits(expected), "immutable cache growth corrupts coordinates");
        }
        final int before = reads.get();
        cached.sum(16384, 1, 2, 3, (i, target, offset) -> { throw new AssertionError("recaptured immutable points"); }, true);
        require(reads.get() == before, "immutable repeated query reread points");
        boolean captureFailed = false;
        try { potential.sum(4096, 0, 0, 0, (i, t, o) -> { throw new IllegalStateException("capture"); }, false); }
        catch (IllegalStateException expected) { captureFailed = true; }
        require(captureFailed, "potential capture exception lost");
        potential.sum(4096, 0, 0, 0, (i, t, o) -> {
            if (i == 0) require(potential.sum(0, 0, 0, 0, (a, b, c) -> {}, false) == 0.0, "reentrant potential");
            System.arraycopy(points[i], 0, t, o, 4);
        }, false);
        if (EscoConfig.WORKERS > 1) require(EscoMetrics.PARALLEL_MERGE_PASSES.sum() > 0, "parallel merge path not exercised");
        int activationChecked = 0;
        final ActivationSnapshot snapshot = new ActivationSnapshot(0, 0);
        for (int fixture = 0; fixture < 60; ++fixture) {
            int n = 500 + random.nextInt(5000), p = 1 + random.nextInt(96);
            snapshot.ensureCapacity(n, p);
            Arrays.fill(snapshot.active, (byte) 1); // stale data must be overwritten, including skipped entities
            for (int i = 0; i < p; ++i) {
                double x = random.nextDouble() * 500 - 250, y = random.nextDouble() * 1000 - 500, z = random.nextDouble() * 500 - 250;
                for (int type = 0; type < 8; ++type) {
                    double range = type == 7 ? 12 + fixture % 8 * 12 : (type + 1) * 12;
                    int b = i * 48 + type * 6;
                    snapshot.players[b] = x - range; snapshot.players[b + 1] = y - 384; snapshot.players[b + 2] = z - range;
                    snapshot.players[b + 3] = x + range; snapshot.players[b + 4] = y + 384; snapshot.players[b + 5] = z + range;
                }
                int q = i * 6, b = i * 48 + 42;
                snapshot.querySections[q] = ((int)Math.floor(snapshot.players[b]) - 2) >> 4;
                snapshot.querySections[q + 1] = ((int)Math.floor(snapshot.players[b + 3]) + 2) >> 4;
                snapshot.querySections[q + 2] = ((int)Math.floor(snapshot.players[b + 2]) - 2) >> 4;
                snapshot.querySections[q + 3] = ((int)Math.floor(snapshot.players[b + 5]) + 2) >> 4;
                snapshot.querySections[q + 4] = Math.clamp((int)Math.floor(snapshot.players[b + 1] - 4) >> 4, -4, 19);
                snapshot.querySections[q + 5] = Math.clamp((int)Math.floor(snapshot.players[b + 4]) >> 4, -4, 19);
            }
            for (int i = 0; i < n; ++i) {
                int b = i * 6, s = i * 3;
                double x = random.nextDouble() * 650 - 325, y = random.nextDouble() * 1000 - 500, z = random.nextDouble() * 650 - 325;
                snapshot.entities[b] = x; snapshot.entities[b + 1] = y; snapshot.entities[b + 2] = z;
                snapshot.entities[b + 3] = x + random.nextDouble() * 12; snapshot.entities[b + 4] = y + 2; snapshot.entities[b + 5] = z + .7;
                snapshot.sections[s] = (int)Math.floor(x) >> 4;
                snapshot.sections[s + 1] = Math.clamp((int)Math.floor(y) >> 4, -4, 19);
                snapshot.sections[s + 2] = (int)Math.floor(z) >> 4;
                snapshot.types[i] = (byte)(random.nextInt(9) - 1);
                snapshot.needsPriorityReset[i] = i % 3 == 0;
            }
            // Independent player-first oracle matching Paper's traversal and activation rules.
            byte[] expected = new byte[n];
            byte[] expectedPriority = new byte[n];
            for (int player = 0; player < p; ++player) {
                for (int entity = 0; entity < n; ++entity) {
                    int type = snapshot.types[entity], q = player * 6, s = entity * 3, e = entity * 6;
                    if (snapshot.sections[s] < snapshot.querySections[q] || snapshot.sections[s] > snapshot.querySections[q + 1]) continue;
                    if (snapshot.sections[s + 2] < snapshot.querySections[q + 2] || snapshot.sections[s + 2] > snapshot.querySections[q + 3]) continue;
                    if (snapshot.sections[s + 1] < snapshot.querySections[q + 4] || snapshot.sections[s + 1] > snapshot.querySections[q + 5]) continue;
                    if (!overlap(snapshot, e, player * 48 + 42)) continue;
                    if (snapshot.needsPriorityReset[entity]) expectedPriority[entity] = 1;
                    if (type < 0) continue;
                    if (type == 7 || overlap(snapshot, e, player * 48 + type * 6)) expected[entity] = 1;
                }
            }
            snapshot.clipToBroadPhase();
            ParallelWork.forRange(n, 128, snapshot::evaluate);
            require(Arrays.equals(snapshot.active, 0, n, expected, 0, n), "activation decision mismatch, fixture " + fixture);
            require(Arrays.equals(snapshot.priorityReset, 0, n, expectedPriority, 0, n), "Leaf priority reset mismatch");
            activationChecked += n;
        }
        final ArrayList<Value> outer = new ArrayList<>();
        for (int i = 100; i >= 0; --i) outer.add(new Value(i, i));
        DistanceOrder.sort(outer, v -> {
            final ArrayList<Value> inner = new ArrayList<>();
            for (int i = 100; i >= 0; --i) inner.add(new Value(i, i));
            DistanceOrder.sort(inner, Value::distance);
            require(inner.getFirst().id == 0, "reentrant sort corrupted workspace");
            return v.distance;
        });
        require(outer.getFirst().id == 0, "outer sort corrupted by nested capture");
        final LinkedList<Value> linked = new LinkedList<>(outer.reversed());
        DistanceOrder.sort(linked, Value::distance);
        require(linked.equals(outer), "non-random-access list order");
        boolean extractorFailed = false;
        try { DistanceOrder.sort(outer, v -> { throw new IllegalStateException("extractor"); }); }
        catch (IllegalStateException expected) { extractorFailed = true; }
        require(extractorFailed, "extractor exception was lost");
        DistanceOrder.sort(outer, Value::distance); // workspace is released even on capture failure
        final var localField = DistanceOrder.class.getDeclaredField("LOCAL");
        localField.setAccessible(true);
        final Object workspace = ((ThreadLocal<?>) localField.get(null)).get();
        final var valuesField = workspace.getClass().getDeclaredField("values");
        valuesField.setAccessible(true);
        for (Object value : (Object[]) valuesField.get(workspace)) require(value == null, "sort workspace retained an object");
        final var useField = workspace.getClass().getDeclaredField("inUse");
        useField.setAccessible(true);
        require(!useField.getBoolean(workspace), "sort workspace was not released");
        AtomicInteger active = new AtomicInteger(), completed = new AtomicInteger();
        boolean failed = false;
        try {
            ParallelWork.forRange(10000, 64, (from, to) -> {
                active.incrementAndGet();
                try {
                    if (from == 0) throw new IllegalStateException("expected");
                    java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                } finally { completed.addAndGet(to - from); active.decrementAndGet(); }
            });
        } catch (IllegalStateException expected) { failed = true; }
        require(failed, "task exception was lost");
        require(active.get() == 0 && completed.get() == 10000, "worker outlived failed barrier");
        final int[] stages = new int[16384];
        ParallelWork.pipeline(() -> {
            for (int pass = 0; pass < 4; ++pass) {
                final int step = pass;
                ParallelWork.stage(stages.length, 256, (from, to) -> {
                    for (int i = from; i < to; ++i) {
                        require(stages[i] == step, "pipeline stage escaped previous barrier");
                        stages[i] = step + 1;
                    }
                });
            }
        });
        for (int value : stages) require(value == 4, "pipeline omitted work");
        completed.set(0); failed = false;
        try {
            ParallelWork.pipeline(() -> ParallelWork.stage(10000, 64, (from, to) -> {
                active.incrementAndGet();
                try {
                    if (from == 0) throw new IllegalStateException("pipeline expected");
                    java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                } finally { completed.addAndGet(to - from); active.decrementAndGet(); }
            }));
        } catch (IllegalStateException expected) { failed = true; }
        require(failed && active.get() == 0 && completed.get() == 10000, "pipeline failure escaped join");
        AtomicInteger nested = new AtomicInteger();
        ParallelWork.forRange(10000, 64, (from, to) -> ParallelWork.forRange(to - from, 1,
            (a, b) -> nested.addAndGet(b - a)));
        require(nested.get() == 10000, "nested range lost work");
        if (EscoConfig.WORKERS > 0) {
            // Force concurrent participation; this verifies thread count, not CPU scaling or speed.
            final CountDownLatch participants = new CountDownLatch(EscoConfig.WORKERS);
            ParallelWork.forRange((EscoConfig.WORKERS + 1) * 1024, 1024, (from, to) -> {
                if (Thread.currentThread().getName().startsWith("Esco-Compute-")) participants.countDown();
                try { require(participants.await(15, TimeUnit.SECONDS), "not all configured workers started"); }
                catch (InterruptedException exception) { throw new AssertionError(exception); }
            });
            require(EscoMetrics.PEAK_WORKERS.get() == EscoConfig.WORKERS, "worker participation mismatch");
            require(EscoMetrics.PEAK_LIVE_WORKERS.get() == EscoConfig.WORKERS, "live worker cap mismatch");
        }
        require(EscoMetrics.ACTIVE_WORKERS.get() == 0, "worker active after final join");
        require(EscoMetrics.PEAK_WORKERS.get() <= EscoConfig.WORKERS, "configured worker cap exceeded");
        require(EscoMetrics.PEAK_LIVE_WORKERS.get() <= EscoConfig.WORKERS, "live worker cap exceeded");
        System.out.println("PASS stable distance values=" + valuesChecked + ", potential contributions=" + potentialValues + ", activation decisions=" + activationChecked + ", caller-thread capture and worker failure barrier");
        System.out.println(EscoMetrics.snapshot());
    }
    private static boolean overlap(ActivationSnapshot s, int a, int b) {
        for (int axis = 0; axis < 3; ++axis) {
            if (!(s.entities[a + axis] < s.players[b + axis + 3] && s.entities[a + axis + 3] > s.players[b + axis])) return false;
        }
        return true;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
