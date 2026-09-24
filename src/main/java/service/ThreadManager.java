package service;

import model.ActivityEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Week 4 Demo: Central executor management + graceful shutdown.
 * Every executor the app owns must have a shutdown path.
 */
public class ThreadManager {

    private static ThreadManager instance;

    // Fixed pool for CPU-bound tasks (StatsCalculator, etc.)
    private final ExecutorService calcPool = Executors.newFixedThreadPool(3);

    // Consumer worker for the SessionQueue
    private final ExecutorService queueWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "queue-consumer");
        t.setDaemon(true);
        return t;
    });

    private final SessionQueue sessionQueue = new SessionQueue();
    private final SessionCounter counter = new SessionCounter();

    private volatile boolean shuttingDown = false;
    private Thread queueThread;

    private ThreadManager() {}

    public static synchronized ThreadManager getInstance() {
        if (instance == null) instance = new ThreadManager();
        return instance;
    }

    public SessionQueue getQueue() { return sessionQueue; }
    public SessionCounter getCounter() { return counter; }
    public ExecutorService getCalcPool() { return calcPool; }

    /**
     * Start the single background consumer that processes queued sessions.
     */
    public void startQueueConsumer(Runnable onConsumed) {
        queueWorker.submit(() -> {
            System.out.println("[" + Thread.currentThread().getName() + "] consumer started");
            while (!shuttingDown) {
                try {
                    ActivityEntry entry = sessionQueue.take(); // blocks via wait()
                    counter.addSession(entry.getDurationSeconds());
                    System.out.println("[" + Thread.currentThread().getName()
                            + "] consumed: " + entry.getName()
                            + " (" + entry.getDurationFormatted() + ")");
                    if (onConsumed != null) javafx.application.Platform.runLater(onConsumed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("[" + Thread.currentThread().getName() + "] consumer exiting");
        });
    }

    /**
     * Submit a Callable task and get back a Future.
     */
    public <T> Future<T> submitCalculation(Callable<T> task) {
        return calcPool.submit(task);
    }

    /**
     * Graceful shutdown pattern from the lab manual.
     */
    public void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        System.out.println("[ThreadManager] shutting down executors...");

        queueWorker.shutdownNow();
        calcPool.shutdown();

        try {
            if (!calcPool.awaitTermination(3, TimeUnit.SECONDS)) {
                calcPool.shutdownNow();
                if (!calcPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("[ThreadManager] calcPool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            calcPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[ThreadManager] shutdown complete");
    }
}