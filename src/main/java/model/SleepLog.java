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
}