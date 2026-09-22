package model;

public class App {
    private String name;
    private int dailyLimitMinutes;
    private int totalSecondsUsed;
    private String category;

    public App(String name, int dailyLimitMinutes, String category) {
        this.name = name;
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.totalSecondsUsed = 0;
        this.category = category;
    }

    public String getName() { return name; }
    public int getDailyLimitMinutes() { return dailyLimitMinutes; }
    public int getTotalSecondsUsed() { return totalSecondsUsed; }
    public void setTotalSecondsUsed(int seconds) { this.totalSecondsUsed = seconds; }
    public String getCategory() { return category; }
}