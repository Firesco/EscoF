package dev.escos.fork.concurrent;

import dev.escos.fork.EscoConfig;
import dev.escos.fork.EscoMetrics;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded fork/join work on primitive snapshots. No task may call a world or plugin API. */
public final class ParallelWork {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ForkJoinPool POOL = EscoConfig.WORKERS == 0 ? null : new ForkJoinPool(
        EscoConfig.WORKERS, Worker::new, null, false,
        EscoConfig.WORKERS, EscoConfig.WORKERS, 1, pool -> true, 60L, TimeUnit.SECONDS);

    private ParallelWork() {}

    private static final class Worker extends ForkJoinWorkerThread {
        Worker(final ForkJoinPool pool) {
            super(pool);
            setName("Esco-Compute-" + IDS.incrementAndGet());
            setDaemon(true);
        }
        @Override
        protected void onStart() {
            super.onStart();
            EscoMetrics.WORKER_NAMES.add(getName());
            EscoMetrics.PEAK_LIVE_WORKERS.accumulateAndGet(EscoMetrics.LIVE_WORKERS.incrementAndGet(), Math::max);
        }
        @Override
        protected void onTermination(final Throwable exception) {
            try { EscoMetrics.LIVE_WORKERS.decrementAndGet(); }
            finally { super.onTermination(exception); }
        }
    }

    @FunctionalInterface
    public interface RangeAction { void run(int from, int to); }

    public static void forRange(final int count, final int grain, final RangeAction action) {
        if (grain < 1) throw new IllegalArgumentException("grain must be positive");
        if (count <= 0) return;
        // A nested invocation stays within its existing worker and cannot deadlock a saturated pool.
        if (POOL == null || count <= grain || ForkJoinTask.getPool() == POOL) {
            action.run(0, count);
            return;
        }
        EscoMetrics.PARALLEL_BATCHES.increment();
        if (EscoConfig.CALLER_COMPUTE) {
            // The caller owns no world mutations during this barrier. Let it process one
            // primitive slice instead of parking while every slice wakes a pool worker.
            final int jobs = (int) Math.min(EscoConfig.WORKERS + 1L, ((long) count + grain - 1) / grain);
            final int split = (int) ((long) (jobs - 1) * count / jobs);
            final BatchTask workers = new BatchTask(split, jobs - 1, action);
            // Submit one coordinator. Its forks remain in worker-local queues instead of
            // repeatedly waking the pool for every externally submitted slice.
            POOL.execute(workers);
            EscoMetrics.POOL_ENTRIES.increment();
            Throwable failure = null;
            try {
                EscoMetrics.CALLER_TASKS.increment();
                action.run(split, count);
            } catch (RuntimeException | Error exception) { failure = exception; }
            try { workers.join(); }
            catch (RuntimeException | Error exception) {
                if (failure == null) failure = exception;
                else if (failure != exception) failure.addSuppressed(exception);
            }
            if (failure instanceof RuntimeException exception) throw exception;
            if (failure instanceof Error error) throw error;
        } else {
            final int jobs = (int) Math.min(EscoConfig.WORKERS * 2L, ((long) count + grain - 1) / grain);
            EscoMetrics.POOL_ENTRIES.increment();
            POOL.invoke(new BatchTask(count, jobs, action));
        }
    }

    /** One caller-to-pool handoff for a sequence of primitive-only stages. */
    public static void pipeline(final Runnable stages) {
        if (POOL == null || EscoConfig.WORKERS < 2 || ForkJoinTask.getPool() == POOL) {
            stages.run();
        } else {
            EscoMetrics.POOL_ENTRIES.increment();
            POOL.invoke(new RecursiveAction() {
                @Override protected void compute() { stages.run(); }
            });
        }
    }

    /** Only pipeline coordinators call this; each stage joins completely before the next. */
    public static void stage(final int count, final int grain, final RangeAction action) {
        if (grain < 1) throw new IllegalArgumentException("grain must be positive");
        if (count <= 0) return;
        if (POOL == null || EscoConfig.WORKERS < 2 || count <= grain) { action.run(0, count); return; }
        if (ForkJoinTask.getPool() != POOL) { forRange(count, grain, action); return; }
        final int jobs = (int) Math.min(EscoConfig.WORKERS, ((long) count + grain - 1) / grain);
        EscoMetrics.PARALLEL_BATCHES.increment();
        new BatchTask(count, jobs, action).invoke();
    }

    private static void joinAll(final LeafTask[] tasks, final int launched, Throwable failure) {
        for (int i = launched - 1; i >= 0; --i) {
            try { tasks[i].join(); }
            catch (RuntimeException | Error exception) {
                if (failure == null) failure = exception;
                else if (failure != exception) failure.addSuppressed(exception);
            }
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }

    private static final class BatchTask extends RecursiveAction {
        private final int count, jobs;
        private final RangeAction action;
        BatchTask(final int count, final int jobs, final RangeAction action) {
            this.count = count; this.jobs = jobs; this.action = action;
        }
        @Override
        protected void compute() {
            // One flat layer avoids the allocations and joins of a recursively split task tree.
            final LeafTask[] tasks = new LeafTask[this.jobs - 1];
            int forked = 0;
            Throwable failure = null;
            try {
                for (int i = 0; i < tasks.length; ++i) {
                    tasks[i] = new LeafTask((int) ((long) i * this.count / this.jobs),
                        (int) ((long) (i + 1) * this.count / this.jobs), this.action);
                    tasks[i].fork();
                    ++forked;
                }
                runMeasured((int) ((long) (this.jobs - 1) * this.count / this.jobs), this.count, this.action);
            } catch (RuntimeException | Error exception) {
                failure = exception;
            }
            // Always join every launched task, including when more than one task fails.
            joinAll(tasks, forked, failure);
        }
    }

    private static final class LeafTask extends RecursiveAction {
        private final int from, to;
        private final RangeAction action;
        LeafTask(final int from, final int to, final RangeAction action) {
            this.from = from; this.to = to; this.action = action;
        }
        @Override
        protected void compute() { runMeasured(this.from, this.to, this.action); }
    }

    private static void runMeasured(final int from, final int to, final RangeAction action) {
        final long started = System.nanoTime();
        EscoMetrics.PEAK_WORKERS.accumulateAndGet(EscoMetrics.ACTIVE_WORKERS.incrementAndGet(), Math::max);
        try { action.run(from, to); }
        finally {
            EscoMetrics.ACTIVE_WORKERS.decrementAndGet();
            EscoMetrics.WORKER_TASKS.increment();
            EscoMetrics.WORKER_NANOS.add(System.nanoTime() - started);
        }
    }
}
