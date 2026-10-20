package au.chipomho.showcase.fleet;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A scheduler whose fixed-rate tasks run only when a test calls {@link #step()}.
 */
public final class ManualScheduler implements TaskScheduler {

    private final List<Task> tasks = new CopyOnWriteArrayList<>();

    /**
     * Runs each task that is still scheduled once, as if one period had passed.
     */
    public void step() {
        tasks.removeIf(Task::isCancelled);
        tasks.forEach(task -> task.runnable.run());
    }

    public int scheduledTasks() {
        tasks.removeIf(Task::isCancelled);
        return tasks.size();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, Duration period) {
        var task = new Task(runnable);
        tasks.add(task);
        return task;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        throw unsupported();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        throw unsupported();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        throw unsupported();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        throw unsupported();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Only fixed-rate tasks without a start time");
    }

    private static final class Task implements ScheduledFuture<Void> {

        private final Runnable runnable;
        private volatile boolean cancelled;

        Task(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Void get() {
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
