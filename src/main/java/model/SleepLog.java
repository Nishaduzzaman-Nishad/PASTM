package model;

import java.time.LocalDate;
import java.time.LocalTime;

public class SleepLog {
    private LocalDate date;
    private LocalTime bedTime;
    private LocalTime wakeTime;

    public SleepLog(LocalDate date, LocalTime bedTime, LocalTime wakeTime) {
        this.date = date;
        this.bedTime = bedTime;
        this.wakeTime = wakeTime;
    }

    public LocalDate getDate() { return date; }
    public LocalTime getBedTime() { return bedTime; }
    public LocalTime getWakeTime() { return wakeTime; }

    private long getDiffSeconds() {
        long bedSeconds = bedTime.toSecondOfDay();
        long wakeSeconds = wakeTime.toSecondOfDay();
        return wakeSeconds >= bedSeconds
                ? wakeSeconds - bedSeconds
                : (24 * 3600) - bedSeconds + wakeSeconds;
    }

    public long getHoursSlept() { return getDiffSeconds() / 3600; }
    public long getMinutesSlept() { return (getDiffSeconds() % 3600) / 60; }
    public long getTotalMinutes() { return getDiffSeconds() / 60; }

    public String getFormattedDuration() {
        return getHoursSlept() + " hours " + getMinutesSlept() + " minutes";
    }
}