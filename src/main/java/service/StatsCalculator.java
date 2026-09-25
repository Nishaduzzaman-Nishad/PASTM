package service;

import model.ActivityEntry;
import java.util.List;
import java.util.concurrent.Callable;

public class StatsCalculator implements Callable<StatsCalculator.Stats> {

    public static class Stats {
        public final int screenSeconds;
        public final int studySeconds;
        public final int sleepSeconds;
        public final int totalSessions;

        public Stats(int screenSeconds, int studySeconds, int sleepSeconds, int totalSessions) {
            this.screenSeconds = screenSeconds;
            this.studySeconds = studySeconds;
            this.sleepSeconds = sleepSeconds;
            this.totalSessions = totalSessions;
        }

        @Override public String toString() {
            return String.format("Screen: %s | Study: %s | Sleep: %s | Sessions: %d",
                    fmt(screenSeconds), fmt(studySeconds), fmt(sleepSeconds), totalSessions);
        }

        private String fmt(int t) {
            return String.format("%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60);
        }
    }

    private final List<ActivityEntry> snapshot;
    public StatsCalculator(List<ActivityEntry> snapshot) { this.snapshot = snapshot; }

    @Override public Stats call() {
        int screen = 0, study = 0, sleep = 0;
        for (ActivityEntry e : snapshot) {
            switch (e.getCategory()) {
                case "Screen Time": screen += e.getDurationSeconds(); break;
                case "Study Time":  study  += e.getDurationSeconds(); break;
                case "Sleep Time":  sleep  += e.getDurationSeconds(); break;
            }
        }
        return new Stats(screen, study, sleep, snapshot.size());
    }
}
