package au.chipomho.showcase.fleet.domain;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.ToDoubleFunction;

/**
 * One truck's readings over the most recent span of time, measured back from its newest reading.
 * Readings must be added in time order.
 */
public final class Window {

    private final Duration span;
    private final Deque<Reading> readings = new ArrayDeque<>();

    Window(Duration span) {
        this.span = span;
    }

    void add(Reading reading) {
        readings.addLast(reading);
        var oldestKept = reading.recordedAt().minus(span);
        while (readings.getFirst().recordedAt().isBefore(oldestKept)) {
            readings.removeFirst();
        }
    }

    public int size() {
        return readings.size();
    }

    public double mean(ToDoubleFunction<Reading> metric) {
        return readings.stream().mapToDouble(metric).average().orElseThrow();
    }
}
