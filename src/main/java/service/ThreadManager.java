package service;

import model.ActivityEntry;

import java.util.concurrent.*;

public class ThreadManager {

    private static ThreadManager instance;
    private final ExecutorService calcPool = Executors.newFixedThreadPool(3);
    private final ExecutorService queueWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "queue-consumer");
        t.setDaemon(true);
        return t;
    });

    private final SessionQueue sessionQueue = new SessionQueue();
    private final SessionCounter counter = new SessionCounter();
    private volatile boolean shuttingDown = false;

    private ThreadManager() {}

    public static synchronized ThreadManager getInstance() {
        if (instance == null) instance = new ThreadManager();
        return instance;
    }

    public SessionQueue getQueue() { return sessionQueue; }
    public SessionCounter getCounter() { return counter; }
    public ExecutorService getCalcPool() { return calcPool; }

    public void startQueueConsumer(Runnable onConsumedUiUpdate) {
        queueWorker.submit(() -> {
            System.out.println("[queue-consumer] started");
            while (!shuttingDown) {
                try {
                    ActivityEntry entry = sessionQueue.take(500, TimeUnit.MILLISECONDS);
                    if (entry == null) continue;

                    try {
                        counter.addSession(entry.getDurationSeconds());
                        if (onConsumedUiUpdate != null) {
                            javafx.application.Platform.runLater(onConsumedUiUpdate);
                        }
                    } catch (Exception innerEx) {
                        System.err.println("[queue-consumer] process failed: " + innerEx.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception outerEx) {
                    System.err.println("[queue-consumer] unexpected: " + outerEx.getMessage());
                }
            }
            System.out.println("[queue-consumer] exiting");
        });
    }

    public <T> Future<T> submitCalculation(Callable<T> task) {
        return calcPool.submit(task);
    }

    public void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        System.out.println("[ThreadManager] shutting down...");

        queueWorker.shutdownNow();
        calcPool.shutdown();

        try {
            if (!calcPool.awaitTermination(3, TimeUnit.SECONDS)) {
                calcPool.shutdownNow();
                calcPool.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            calcPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[ThreadManager] shutdown complete");
    }
}