package dev.escos.fork.ai;

import dev.escos.fork.EscoConfig;
import dev.escos.fork.EscoMetrics;
import dev.escos.fork.concurrent.ParallelWork;
import java.util.Arrays;
import java.util.RandomAccess;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Stable distance ordering: capture on the caller; workers see only double[] and int[]. */
public final class DistanceOrder {
    private static final ThreadLocal<Workspace> LOCAL = ThreadLocal.withInitial(Workspace::new);
    private static final ThreadLocal<int[]> HISTOGRAM = ThreadLocal.withInitial(() -> new int[2048]);
    private DistanceOrder() {}

    private static final class Workspace {
        Object[] values = new Object[0];
        double[] distances = new double[0];
        long[] keys = new long[0];
        int[] order = new int[0], scratch = new int[0];
        boolean inUse;
        void ensureCapacity(final int size) {
            if (size <= this.values.length) return;
            final int capacity = Math.max(size, this.values.length + (this.values.length >> 1));
            this.values = new Object[capacity];
            this.distances = new double[capacity];
            this.keys = new long[capacity];
            this.order = new int[capacity];
            this.scratch = new int[capacity];
            EscoMetrics.SORT_BUFFER_GROWS.increment();
        }
    }

    public static <T> void sort(final List<T> list, final ToDoubleFunction<? super T> distance) {
        final int size = list.size();
        if (!EscoConfig.SENSORS || size < 64 || !(list instanceof RandomAccess)) {
            list.sort(Comparator.comparingDouble(distance));
            return;
        }
        final Workspace workspace = LOCAL.get();
        if (workspace.inUse) {
            list.sort(Comparator.comparingDouble(distance));
            return;
        }
        workspace.inUse = true;
        try {
            workspace.ensureCapacity(size);
            final Object[] values = list.toArray(workspace.values);
            final double[] distances = workspace.distances;
            final long[] keys = workspace.keys;
            final int[] order = workspace.order;
            final int[] scratch = workspace.scratch;
            boolean sorted = true;
            for (int i = 0; i < size; ++i) {
                @SuppressWarnings("unchecked") final T value = (T) values[i];
                distances[i] = distance.applyAsDouble(value);
                final long bits = Double.doubleToLongBits(distances[i]);
                keys[i] = bits < 0 ? ~bits : bits ^ Long.MIN_VALUE;
                order[i] = i;
                if (i > 0 && Double.compare(distances[i - 1], distances[i]) > 0) sorted = false;
            }
            EscoMetrics.SORTS.increment();
            EscoMetrics.SORTED_ITEMS.add(size);
            if (sorted) return;
            final boolean parallel = size >= EscoConfig.SORT_PARALLEL_THRESHOLD && EscoConfig.WORKERS > 1;
            if (!parallel) {
                // A single linear radix pass sequence avoids both handoffs and merge levels
                // for sizes where coordination costs more than the work it distributes.
                radixRange(keys, order, scratch, 0, size);
            } else if (EscoConfig.SORT_PIPELINE) {
                // Closure owns primitive arrays only; extraction and list publication stay on caller.
                ParallelWork.pipeline(() -> sortIndices(keys, distances, order, scratch, size, true, true));
            } else sortIndices(keys, distances, order, scratch, size, parallel, false);
            for (int i = 0; i < size; ++i) {
                @SuppressWarnings("unchecked") final T value = (T) values[order[i]];
                list.set(i, value);
            }
        } finally {
            Arrays.fill(workspace.values, 0, Math.min(size, workspace.values.length), null);
            workspace.inUse = false;
        }
    }

    private static void sortIndices(final long[] keys, final double[] distances, final int[] order,
                                    final int[] scratch, final int size, final boolean parallel, final boolean pipeline) {
            final int run = 4096;
            final int runs = (size + run - 1) / run;
            final ParallelWork.RangeAction sortRuns = (from, to) -> {
                for (int batch = from; batch < to; ++batch) {
                    radixRange(keys, order, scratch, batch * run, Math.min(size, (batch + 1) * run));
                }
            };
            if (parallel) dispatch(pipeline, runs, sortRuns);
            else sortRuns.run(0, runs);
            int[] source = order, destination = scratch;
            // Split by output rank, including the final merge. Splitting only by merge-pair
            // leaves the largest (and most expensive) levels on a single worker.
            final int grain = 2048;
            final int chunks = (size + grain - 1) / grain;
            for (int width = run; width < size; width *= 2) {
                final int span = width;
                final int[] input = source, output = destination;
                final ParallelWork.RangeAction mergeSlices = (from, to) -> {
                    for (int chunk = from; chunk < to; ++chunk) {
                        final int start = chunk * grain;
                        final int base = (int) (start / (span * 2L) * (span * 2L));
                        final int middle = Math.min(size, base + span);
                        final int end = (int) Math.min(size, base + span * 2L);
                        mergeSlice(distances, input, output, base, middle, end,
                            start, Math.min(size, start + grain));
                    }
                };
                if (parallel) {
                    if (EscoConfig.WORKERS > 0 && chunks > 1) EscoMetrics.PARALLEL_MERGE_PASSES.increment();
                    dispatch(pipeline, chunks, mergeSlices);
                } else mergeSlices.run(0, chunks);
                source = output;
                destination = input;
                if (width > size / 2) break;
            }
        if (source != order) System.arraycopy(source, 0, order, 0, size);
    }

    private static void dispatch(final boolean pipeline, final int count, final ParallelWork.RangeAction action) {
        if (pipeline) ParallelWork.stage(count, 1, action);
        else ParallelWork.forRange(count, 1, action);
    }

    /** Stable LSD radix on Double.compare sortable IEEE keys; skip identical digit passes. */
    private static void radixRange(final long[] keys, final int[] order, final int[] scratch, final int from, final int to) {
        if (to - from < 2) return;
        long varying = 0;
        final long first = keys[order[from]];
        for (int i = from + 1; i < to; ++i) varying |= first ^ keys[order[i]];
        final int[] histogram = HISTOGRAM.get();
        int[] source = order, destination = scratch;
        for (int shift = 0; shift < 64; shift += 11) {
            if (((varying >>> shift) & 2047L) == 0) continue;
            Arrays.fill(histogram, 0);
            for (int i = from; i < to; ++i) ++histogram[(int) ((keys[source[i]] >>> shift) & 2047L)];
            int offset = from;
            for (int i = 0; i < histogram.length; ++i) {
                final int count = histogram[i]; histogram[i] = offset; offset += count;
            }
            for (int i = from; i < to; ++i) {
                final int index = source[i];
                destination[histogram[(int) ((keys[index] >>> shift) & 2047L)]++] = index;
            }
            final int[] swap = source; source = destination; destination = swap;
        }
        if (source != order) System.arraycopy(source, from, order, from, to - from);
    }

    private static void mergeSlice(final double[] keys, final int[] input, final int[] output,
                                   final int base, final int middle, final int end,
                                   final int from, final int to) {
        if (middle == end || Double.compare(keys[input[middle - 1]], keys[input[middle]]) <= 0) {
            System.arraycopy(input, from, output, from, to - from);
            return;
        }
        final int rank = from - base;
        final int leftSize = middle - base, rightSize = end - middle;
        int low = Math.max(0, rank - rightSize), high = Math.min(rank, leftSize);
        int left = low, right = rank - low;
        // Stable co-ranking: equal keys from the left run always precede the right run.
        while (low <= high) {
            left = (low + high) >>> 1;
            right = rank - left;
            if (left > 0 && right < rightSize
                && Double.compare(keys[input[base + left - 1]], keys[input[middle + right]]) > 0) {
                high = left - 1;
            } else if (right > 0 && left < leftSize
                && Double.compare(keys[input[middle + right - 1]], keys[input[base + left]]) >= 0) {
                low = left + 1;
            } else break;
        }
        left += base;
        right += middle;
        for (int out = from; out < to; ++out) {
            if (left < middle && (right == end || Double.compare(keys[input[left]], keys[input[right]]) <= 0)) {
                output[out] = input[left++];
            } else output[out] = input[right++];
        }
    }

}
