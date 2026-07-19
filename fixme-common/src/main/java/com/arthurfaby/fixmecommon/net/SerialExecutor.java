package com.arthurfaby.fixmecommon.net;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SerialExecutor {

    private final Executor delegate;
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SerialExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    public void execute(Runnable task) {
        tasks.offer(wrap(task));
        if (running.compareAndSet(false, true)) {
            drainOrRelease();
        }
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } finally {
                drainOrRelease();
            }
        };
    }

    private void drainOrRelease() {
        Runnable next = tasks.poll();
        if (next != null) {
            delegate.execute(next);
            return;
        }
        running.set(false);
        if (!tasks.isEmpty() && running.compareAndSet(false, true)) {
            drainOrRelease();
        }
    }
}
