package com.example.processor.scheduler;

import com.example.processor.scheduler.data.VideoTask;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class TaskQueue {

    private final Deque<VideoTask> fast = new ArrayDeque<>();
    private final Deque<VideoTask> slow = new ArrayDeque<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();

    public void lock() { lock.lock(); }
    public void unlock() { lock.unlock(); }

    public int fastSize() { return fast.size(); }
    public int slowSize() { return slow.size(); }

    public void addFast(VideoTask task) { fast.addLast(task); }
    public void addSlow(VideoTask task) { slow.addLast(task); }

    public VideoTask pollFast() { return fast.pollFirst(); }
    public VideoTask pollSlow() { return slow.pollFirst(); }

    public void signalAll() { available.signalAll(); }
    public void await() throws InterruptedException { available.await(); }
}