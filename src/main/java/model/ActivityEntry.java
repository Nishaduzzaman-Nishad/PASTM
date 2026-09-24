package model;

public class ActivityEntry {
    private String name;
    private String category;
    private int durationSeconds;

    public ActivityEntry(String name, String category, int durationSeconds) {
        this.name = name;
        this.category = category;
        this.durationSeconds = durationSeconds;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getDurationSeconds() { return durationSeconds; }
    public void addDuration(int s) { this.durationSeconds += s; }
    public void setDurationSeconds(int s) { this.durationSeconds = s; }

    public String getDurationFormatted() {
        int m = durationSeconds / 60;
        int s = durationSeconds % 60;
        if (m >= 60) return String.format("%dh %02dm", m / 60, m % 60);
        return String.format("%dm %02ds", m, s);
    }

    // NEW: used by the queue consumer
    public ActivityEntry copy() {
        return new ActivityEntry(name, category, durationSeconds);
    }
}