package model;

import java.time.LocalDate;

public class Goal {
    private String name;
    private int targetSeconds;
    private int currentSeconds;
    private String category;
    private boolean completed;
    private String date;

    public Goal(String name, int targetMinutes, String category) {
        this(name, targetMinutes, category, LocalDate.now().toString());
    }

    public Goal(String name, int targetMinutes, String category, String date) {
        this.name = name;
        this.targetSeconds = targetMinutes * 60;
        this.category = category;
        this.date = date;
        this.currentSeconds = 0;
        recalculate();
    }

    public void addProgress(int seconds) {
        this.currentSeconds += seconds;
        recalculate();
    }

    public void resetProgress() {
        this.currentSeconds = 0;
        recalculate();
    }

    private void recalculate() {
        if ("Screen Time".equals(category)) {
            completed = currentSeconds >= targetSeconds * 0.9
                    && currentSeconds <= targetSeconds;
        } else {
            completed = currentSeconds >= targetSeconds;
        }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTargetMinutes() { return targetSeconds / 60; }
    public int getTargetSeconds() { return targetSeconds; }
    public void setTargetMinutes(int t) { this.targetSeconds = t * 60; recalculate(); }
    public int getCurrentMinutes() { return currentSeconds / 60; }
    public int getCurrentSeconds() { return currentSeconds; }
    public String getCategory() { return category; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public boolean isCompleted() { return completed; }

    public double getProgressRatio() {
        if (targetSeconds <= 0) return 0;
        return Math.min(1.0, (double) currentSeconds / targetSeconds);
    }

    public String getProgressText() {
        return formatSec(currentSeconds) + " / " + formatSec(targetSeconds);
    }

    private String formatSec(int s) {
        int m = s / 60;
        if (m >= 60) return String.format("%dh %02dm", m / 60, m % 60);
        return m + "m";
    }
}