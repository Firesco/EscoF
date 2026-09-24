package dev.escos.fork;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicInteger;

/** Cumulative counters. They report work, not an inferred performance gain. */
public final class EscoMetrics {
    public static final LongAdder ACTIVATION_BATCHES = new LongAdder();
    public static final LongAdder ACTIVATION_ENTITIES = new LongAdder();
    public static final LongAdder ACTIVATION_NANOS = new LongAdder();
    public static final LongAdder ACTIVATION_VERIFICATIONS = new LongAdder();
    public static final LongAdder ACTIVATION_MISMATCHES = new LongAdder();
    public static final LongAdder ACTIVATION_BUFFER_GROWS = new LongAdder();
    public static final LongAdder SORT_BUFFER_GROWS = new LongAdder();
    public static final LongAdder PARALLEL_MERGE_PASSES = new LongAdder();
    public static final LongAdder POTENTIAL_BUFFER_GROWS = new LongAdder();
    public static final LongAdder POTENTIAL_QUERIES = new LongAdder();
    public static final LongAdder POTENTIAL_POINTS = new LongAdder();
    public static final AtomicInteger LIVE_WORKERS = new AtomicInteger();
    public static final AtomicInteger PEAK_LIVE_WORKERS = new AtomicInteger();
    public static final LongAdder POI_EMPTY_SEARCHES = new LongAdder();
    public static final LongAdder SORTS = new LongAdder();
    public static final LongAdder SORTED_ITEMS = new LongAdder();
    public static final LongAdder PARALLEL_BATCHES = new LongAdder();
    public static final LongAdder WORKER_TASKS = new LongAdder();
    public static final LongAdder CALLER_TASKS = new LongAdder();
    public static final LongAdder POOL_ENTRIES = new LongAdder();
    public static final LongAdder WORKER_NANOS = new LongAdder();
    public static final AtomicInteger ACTIVE_WORKERS = new AtomicInteger();
    public static final AtomicInteger PEAK_WORKERS = new AtomicInteger();
    public static final Set<String> WORKER_NAMES = ConcurrentHashMap.newKeySet();

    private EscoMetrics() {}

    public static Map<String, Long> snapshot() {
        final Map<String, Long> values = new LinkedHashMap<>();
        values.put("configured_workers", (long) EscoConfig.WORKERS);
        values.put("observed_workers", (long) WORKER_NAMES.size());
        values.put("peak_parallel_workers", (long) PEAK_WORKERS.get());
        values.put("live_workers", (long) LIVE_WORKERS.get());
        values.put("peak_live_workers", (long) PEAK_LIVE_WORKERS.get());
        values.put("active_workers", (long) ACTIVE_WORKERS.get());
        values.put("activation_buffer_grows", ACTIVATION_BUFFER_GROWS.sum());
        values.put("sort_buffer_grows", SORT_BUFFER_GROWS.sum());
        values.put("parallel_batches", PARALLEL_BATCHES.sum());
        values.put("worker_tasks", WORKER_TASKS.sum());
        values.put("caller_compute_tasks", CALLER_TASKS.sum());
        values.put("external_pool_entries", POOL_ENTRIES.sum());
        values.put("worker_wall_nanos_sum", WORKER_NANOS.sum());
        values.put("activation_batches", ACTIVATION_BATCHES.sum());
        values.put("activation_entities", ACTIVATION_ENTITIES.sum());
        values.put("activation_nanos", ACTIVATION_NANOS.sum());
        values.put("activation_verifications", ACTIVATION_VERIFICATIONS.sum());
        values.put("activation_mismatches", ACTIVATION_MISMATCHES.sum());
        values.put("poi_empty_searches", POI_EMPTY_SEARCHES.sum());
        values.put("sensor_sorts", SORTS.sum());
        values.put("sensor_sorted_items", SORTED_ITEMS.sum());
        values.put("sensor_parallel_merge_passes", PARALLEL_MERGE_PASSES.sum());
        values.put("potential_buffer_grows", POTENTIAL_BUFFER_GROWS.sum());
        values.put("potential_queries", POTENTIAL_QUERIES.sum());
        values.put("potential_points", POTENTIAL_POINTS.sum());
        return values;
    }
}
