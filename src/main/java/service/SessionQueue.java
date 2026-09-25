package service;

import model.ActivityEntry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SessionQueue {
    private final LinkedBlockingQueue<ActivityEntry> queue = new LinkedBlockingQueue<>(500);

    public boolean put(ActivityEntry entry) {
        return queue.offer(entry);
    }

    public ActivityEntry take(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int pending() { return queue.size(); }
    public void clear() { queue.clear(); }
}