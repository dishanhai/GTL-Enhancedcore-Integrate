package com.gtl.enhancedcore.client.crafting;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** One display calculation and at most one replacement; jobs never capture a screen or widget. */
public final class CraftingTreeJobs {
    private static final Object LOCK = new Object();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "EnhancedCore-CraftingTree");
                thread.setDaemon(true);
                return thread;
            });
    private static Job<?> latest;

    static { EXECUTOR.allowCoreThreadTimeOut(true); }
    private CraftingTreeJobs() {}

    public static <T> Job<T> submit(Supplier<T> work, Runnable stop, Runnable cleanup) {
        Job<T> job = new Job<>(work, stop, cleanup);
        synchronized (LOCK) {
            if (latest != null) latest.cancel();
            latest = job;
            try {
                EXECUTOR.execute(job);
            } catch (RuntimeException rejected) {
                latest = null;
                job.cancel();
                job.release();
                job.future.completeExceptionally(rejected);
            }
        }
        return job;
    }

    public static final class Job<T> implements Runnable {
        private Supplier<T> work;
        private Runnable stop;
        private Runnable cleanup;
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private volatile boolean cancelled;

        private Job(Supplier<T> work, Runnable stop, Runnable cleanup) {
            this.work = work;
            this.stop = stop;
            this.cleanup = cleanup;
        }

        public CompletableFuture<T> future() { return future; }
        public boolean cancelled() { return cancelled; }

        public void cancel() {
            cancelled = true;
            Runnable action = stop;
            if (action != null) action.run();
            future.cancel(false);
            if (EXECUTOR.remove(this)) release();
            synchronized (LOCK) { if (latest == this) latest = null; }
        }

        @Override public void run() {
            try {
                Supplier<T> task = work;
                if (!cancelled && task != null) future.complete(task.get());
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            } finally {
                release();
                synchronized (LOCK) { if (latest == this) latest = null; }
            }
        }

        private synchronized void release() {
            Runnable action = cleanup;
            cleanup = null;
            work = null;
            stop = null;
            if (action != null) action.run();
        }
    }
}
