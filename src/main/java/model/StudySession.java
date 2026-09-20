package model;

import java.time.LocalDate;

public class StudySession {
    private String subject;
    private int minutesStudied;
    private LocalDate date;

    public StudySession(String subject, int minutesStudied, LocalDate date) {
        this.subject = subject;
        this.minutesStudied = minutesStudied;
        this.date = date;
    }

    public String getSubject() { return subject; }
    public int getMinutesStudied() { return minutesStudied; }
    public LocalDate getDate() { return date; }
}