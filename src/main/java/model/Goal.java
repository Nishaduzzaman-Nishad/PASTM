package model;

public class Goal {
    private String name;
    private int targetMinutes;
    private int currentMinutes;
    private String category;
    private boolean completed;

    public Goal(String name, int targetMinutes, String category) {
        this.name = name;
        this.targetMinutes = targetMinutes;
        this.category = category;
        this.currentMinutes = 0;
        recalculate();
    }

    public void addProgress(int minutes) {
        this.currentMinutes += minutes;
        recalculate();
    }

    public void resetProgress() {
        this.currentMinutes = 0;
        this.completed = false;
    }

    private void recalculate() {
        if ("Screen Time".equals(category)) {
            // Screen goal = maximum budget. Completed only if some usage and under limit.
            completed = currentMinutes > 0 && currentMinutes <= targetMinutes;
        } else {
            // Study / Sleep goals = minimum target.
            completed = currentMinutes >= targetMinutes;
        }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTargetMinutes() { return targetMinutes; }
    public void setTargetMinutes(int t) { this.targetMinutes = t; recalculate(); }
    public int getCurrentMinutes() { return currentMinutes; }
    public String getCategory() { return category; }
    public boolean isCompleted() { return completed; }

    public double getProgressRatio() {
        if (targetMinutes <= 0) return 0;
        return Math.min(1.0, (double) currentMinutes / targetMinutes);
    }

    public String getProgressText() {
        return currentMinutes + "m / " + targetMinutes + "m";
    }
}