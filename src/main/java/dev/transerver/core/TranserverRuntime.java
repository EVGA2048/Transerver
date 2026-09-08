package dev.transerver.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TranserverRuntime implements AutoCloseable {
    private final TranserverNode node;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();

    public TranserverRuntime(TranserverNode node, Duration interval) {
        this.node = Objects.requireNonNull(node, "node");
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.toMillis() < 1) {
            throw new IllegalArgumentException("interval must be positive");
        }
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "transerver-node");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executor.scheduleWithFixedDelay(node::pumpSafely, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void wake() {
        if (!executor.isShutdown()) {
            executor.execute(node::pumpSafely);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
