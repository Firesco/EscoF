package dev.escos.fork.entity;

import dev.escos.fork.EscoMetrics;
import dev.escos.fork.EscoConfig;
import dev.escos.fork.concurrent.ParallelWork;

/** Spawn-density contributions on primitive snapshots, summed in vanilla order. */
public final class PotentialWork {
    private final Workspace workspace = new Workspace();

    @FunctionalInterface
    public interface ChargeReader {
        /** Called on the caller only. Write x, y, z, charge at offset..offset+3. */
        void copy(int index, double[] target, int offset);
    }

    private static final class Workspace {
        double[] points = new double[0], contributions = new double[0];
        double[] xs = new double[0], ys = new double[0], zs = new double[0], charges = new double[0];
        boolean inUse;
        int captured;
        void ensureCapacity(final int size) {
            if (size <= this.contributions.length) return;
            final int capacity = Math.max(size, this.contributions.length + (this.contributions.length >> 1));
            this.points = new double[EscoConfig.POTENTIAL_SOA ? 4 : Math.multiplyExact(capacity, 4)];
            this.contributions = new double[capacity];
            if (EscoConfig.POTENTIAL_SOA) {
                this.xs = new double[capacity]; this.ys = new double[capacity];
                this.zs = new double[capacity]; this.charges = new double[capacity];
            }
            this.captured = 0;
            EscoMetrics.POTENTIAL_BUFFER_GROWS.increment();
        }
    }

    public double sum(final int count, final double x, final double y, final double z,
                      final ChargeReader reader, final boolean appendOnlyImmutable) {
        final Workspace local = this.workspace;
        final Workspace workspace = local.inUse ? new Workspace() : local;
        workspace.inUse = true;
        try {
            workspace.ensureCapacity(count);
            final double[] points = workspace.points, contributions = workspace.contributions;
            // Vanilla uses an append-only list of immutable BlockPos values. Capture new
            // points once; a MutableBlockPos caller explicitly requests full recapture.
            final int first = appendOnlyImmutable && workspace.captured <= count ? workspace.captured : 0;
            for (int i = first; i < count; ++i) {
                reader.copy(i, points, EscoConfig.POTENTIAL_SOA ? 0 : i * 4);
                if (EscoConfig.POTENTIAL_SOA) {
                    workspace.xs[i] = points[0]; workspace.ys[i] = points[1];
                    workspace.zs[i] = points[2]; workspace.charges[i] = points[3];
                }
            }
            workspace.captured = count;
            if (EscoConfig.POTENTIAL_SOA) {
                final double[] xs = workspace.xs, ys = workspace.ys, zs = workspace.zs, charges = workspace.charges;
                ParallelWork.forRange(count, EscoConfig.POTENTIAL_GRAIN, (from, to) -> {
                    for (int i = from; i < to; ++i) {
                        final double dx = xs[i] - x, dy = ys[i] - y, dz = zs[i] - z;
                        final double distance = dx * dx + dy * dy + dz * dz;
                        contributions[i] = distance == 0.0 ? Double.POSITIVE_INFINITY : charges[i] / Math.sqrt(distance);
                    }
                });
            } else ParallelWork.forRange(count, 1024, (from, to) -> {
                for (int i = from; i < to; ++i) {
                    final int offset = i * 4;
                    final double dx = points[offset] - x;
                    final double dy = points[offset + 1] - y;
                    final double dz = points[offset + 2] - z;
                    final double distance = dx * dx + dy * dy + dz * dz;
                    contributions[i] = distance == 0.0 ? Double.POSITIVE_INFINITY : points[offset + 3] / Math.sqrt(distance);
                }
            });
            // A parallel reduction would regroup floating-point additions and could change
            // a spawn decision at a threshold. Keep exactly the original addition order.
            double sum = 0.0;
            for (int i = 0; i < count; ++i) sum += contributions[i];
            EscoMetrics.POTENTIAL_QUERIES.increment();
            EscoMetrics.POTENTIAL_POINTS.add(count);
            return sum;
        } finally {
            workspace.inUse = false;
        }
    }
}
