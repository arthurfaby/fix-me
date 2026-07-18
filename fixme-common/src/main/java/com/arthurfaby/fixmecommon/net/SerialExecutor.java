package com.arthurfaby.fixmecommon.net;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Garantit qu'au plus une tache a la fois, dans l'ordre de soumission,
 * s'execute pour une connexion donnee - meme si les taches sont soumises
 * a un pool partage par plusieurs threads.
 */
public final class SerialExecutor {

    private final Executor delegate;
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SerialExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    /**
     * Met la tache en file. Si aucune tache de cette instance n'est
     * actuellement en cours d'execution sur le pool, en declenche le
     * traitement.
     */
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
        // Course possible : une tache a pu etre offerte juste apres notre poll()
        // mais avant ce running.set(false), auquel cas son emetteur a trouve
        // running encore vrai et n'a rien declenche. On revérifie donc la file
        // et on reprend la main si besoin.
        if (!tasks.isEmpty() && running.compareAndSet(false, true)) {
            drainOrRelease();
        }
    }
}
