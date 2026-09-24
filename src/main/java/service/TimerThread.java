package service;

import javafx.application.Platform;

/**
 * Week 4 Demo: Extending Thread + volatile flag + interrupt().
 * This is our "NumberThread" from the lab manual, adapted to tick a UI label.
 */
public class TimerThread extends Thread {
    // volatile ensures visibility across threads
    private volatile boolean running = true;
    private int seconds = 0;
    private final TickListener listener;

    public interface TickListener {
        void onTick(int seconds);   // called on the FX thread
        void onFinish(int seconds); // called on the FX thread
    }

    public TimerThread(String name, TickListener listener) {
        super(name); // thread naming convention from the lab
        this.listener = listener;
        setDaemon(true); // don't block JVM exit
    }

    @Override
    public void run() {
        try {
            while (running) {
                Thread.sleep(1000); // may throw InterruptedException
                if (!running) break;
                seconds++;
                final int s = seconds;
                Platform.runLater(() -> listener.onTick(s));
            }
        } catch (InterruptedException e) {
            // Restore the interrupted flag (lab manual pattern)
            Thread.currentThread().interrupt();
            System.out.println(getName() + " interrupted at " + seconds + "s");
        }
        final int finalSeconds = seconds;
        Platform.runLater(() -> listener.onFinish(finalSeconds));
    }

    /**
     * Cooperative stop: flip the flag AND interrupt sleep().
     */
    public void stopTimer() {
        running = false;
        this.interrupt();
    }

    public int getElapsedSeconds() { return seconds; }
    public String getStateName() { return getState().name(); }
}