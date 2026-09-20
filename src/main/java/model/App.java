package model;

public class App {
    private String name;
    private int dailyLimitMinutes;
    private int totalSecondsUsed;

    public App(String name, int dailyLimitMinutes) {
        this.name = name;
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.totalSecondsUsed = 0;
    }

    // Getters and Setters (Encapsulation - Week 1)
    public String getName() { return name; }
    public int getDailyLimitMinutes() { return dailyLimitMinutes; }
    public int getTotalSecondsUsed() { return totalSecondsUsed; }
    public void setTotalSecondsUsed(int seconds) { this.totalSecondsUsed = seconds; }
}