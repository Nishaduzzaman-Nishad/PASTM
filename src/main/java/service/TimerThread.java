package service;

import javafx.application.Platform;

public class TimerThread extends Thread {
    private volatile boolean running = true;
    private int seconds = 0;
    private final TickListener listener;

    public interface TickListener {
        void onTick(int seconds);
        void onFinish(int seconds);
    }

    public TimerThread(String name, TickListener listener) {
        super(name);
        this.listener = listener;
        setDaemon(true);
    }

    @Override
    public void run() {
        long startNanos = System.nanoTime();
        int lastBroadcast = -1;

        try {
            while (running) {
                Thread.sleep(100);
                if (!running) break;

                long elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000L;
                int newSeconds = (int) elapsedSeconds;

                if (newSeconds != lastBroadcast) {
                    lastBroadcast = newSeconds;
                    seconds = newSeconds;
                    final int s = newSeconds;
                    Platform.runLater(() -> listener.onTick(s));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        final int finalSeconds = seconds;
        Platform.runLater(() -> listener.onFinish(finalSeconds));
    }

    public void stopTimer() {
        running = false;
        this.interrupt();
    }

    public int getElapsedSeconds() { return seconds; }
}