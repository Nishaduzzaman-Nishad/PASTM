package application;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import service.SessionCounter;
import service.ThreadManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ThreadMonitorController {

    @FXML private Label stateLabel;
    @FXML private Label queueLabel;
    @FXML private Label counterLabel;
    @FXML private TextArea raceLog;
    @FXML private Label raceResult;
    @FXML private Label safeResult;

    private ScheduledExecutorService refresher;

    private static class UnsafeCounter {
        int count;
        void increment() { count++; }
    }

    @FXML
    public void initialize() {
        refresher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "monitor-refresher");
            t.setDaemon(true);
            return t;
        });
        refresher.scheduleAtFixedRate(() -> Platform.runLater(this::refresh), 0, 500, TimeUnit.MILLISECONDS);
    }

    /** Called by the parent window when closing. */
    public void stop() {
        if (refresher != null && !refresher.isShutdown()) {
            refresher.shutdownNow();
            System.out.println("[ThreadMonitor] refresher stopped");
        }
    }

    private void refresh() {
        Thread main = Thread.currentThread();
        stateLabel.setText("Main thread: " + main.getName() + "  |  State: " + main.getState());

        ThreadManager tm = ThreadManager.getInstance();
        queueLabel.setText("Queue pending: " + tm.getQueue().pending());

        SessionCounter sc = tm.getCounter();
        counterLabel.setText("Synchronized counter → " + sc.getSummary());
    }

    @FXML
    private void runRaceDemo() {
        raceLog.clear();
        UnsafeCounter unsafe = new UnsafeCounter();
        Runnable task = () -> { for (int i = 0; i < 100_000; i++) unsafe.increment(); };
        Thread t1 = new Thread(task, "unsafe-1");
        Thread t2 = new Thread(task, "unsafe-2");
        t1.start(); t2.start();
        try { t1.join(); t2.join(); } catch (InterruptedException ignored) {}
        raceLog.appendText("Expected: 200000\nActual:   " + unsafe.count + "\n");
        raceResult.setText("Unsafe result: " + unsafe.count + " (RACE CONDITION!)");
    }

    @FXML
    private void runSafeDemo() {
        SessionCounter safe = new SessionCounter();
        Runnable task = () -> { for (int i = 0; i < 100_000; i++) safe.addSession(1); };
        Thread t1 = new Thread(task, "safe-1");
        Thread t2 = new Thread(task, "safe-2");
        t1.start(); t2.start();
        try { t1.join(); t2.join(); } catch (InterruptedException ignored) {}
        safeResult.setText("Safe sessions: " + safe.getSessionCount() + " (NO lost updates)");
    }
}