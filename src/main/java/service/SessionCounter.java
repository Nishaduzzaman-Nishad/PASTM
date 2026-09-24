package service;

/**
 * Week 4 Demo: Shared Mutable State protected with synchronized.
 *
 * Without synchronized, two threads calling addSession() could lose updates
 * (this is the "UnsafeCounter" race condition from the lab manual).
 */
public class SessionCounter {
    private int totalSeconds;
    private int sessionCount;
    private int totalMinutes;

    public synchronized void addSession(int seconds) {
        // Compound operation: read + compute + write — must be atomic
        this.totalSeconds += seconds;
        this.sessionCount++;
        this.totalMinutes += seconds / 60;
    }

    public synchronized void reset() {
        totalSeconds = 0;
        sessionCount = 0;
        totalMinutes = 0;
    }

    public synchronized int getTotalSeconds() { return totalSeconds; }
    public synchronized int getSessionCount() { return sessionCount; }
    public synchronized int getTotalMinutes() { return totalMinutes; }

    public synchronized String getSummary() {
        return String.format("Sessions: %d | Total: %s",
                sessionCount, formatHMS(totalSeconds));
    }

    private String formatHMS(int t) {
        return String.format("%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60);
    }
}