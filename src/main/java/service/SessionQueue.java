package service;

import model.ActivityEntry;

/**
 * Week 4 Demo: Producer-Consumer with wait() and notifyAll().
 * One-slot buffer (mirrors the "Box" example from the lab manual).
 */
public class SessionQueue {
    private ActivityEntry item;
    private boolean hasItem = false;

    /**
     * Producer: UI thread calls this when a session ends.
     * Uses while() (not if) to re-check the guarded condition.
     */
    public synchronized void put(ActivityEntry entry) throws InterruptedException {
        while (hasItem) {
            wait(); // release lock and pause
        }
        item = entry;
        hasItem = true;
        notifyAll(); // wake the consumer
    }

    /**
     * Consumer: background worker calls this to process sessions.
     */
    public synchronized ActivityEntry take() throws InterruptedException {
        while (!hasItem) {
            wait(); // wait until producer fills the slot
        }
        ActivityEntry result = item;
        item = null;
        hasItem = false;
        notifyAll(); // wake the producer
        return result;
    }

    public synchronized int pending() {
        return hasItem ? 1 : 0;
    }
}