package au.chipomho.showcase.fleet.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps a rolling window per truck and applies the rules to it. An alert is raised once, when
 * its condition starts, and not again until its rule says the condition has cleared. The one
 * exception is escalation: a warning that gets worse past the critical limit raises a critical
 * alert, once.
 *
 * <p>The state lives in memory, so after a restart each truck's window refills from new readings
 * before anything is raised.
 */
public final class AlertMonitor {

    private final List<AlertRule> rules;
    private final Duration window;
    private final int minimumReadings;
    private final Map<String, Watch> watches = new HashMap<>();

    /**
     * @param minimumReadings how many readings the window needs before it is judged, so that one
     *                        odd reading can't raise an alert on its own
     */
    public AlertMonitor(List<AlertRule> rules, Duration window, int minimumReadings) {
        this.rules = List.copyOf(rules);
        this.window = window;
        this.minimumReadings = minimumReadings;
    }

    /**
     * Adds a reading, newer than the truck's last, and returns the alerts that start with it.
     */
    public synchronized List<Breach> record(Reading reading) {
        var watch = watches.computeIfAbsent(reading.truckId(), _ -> new Watch(new Window(window), new EnumMap<>(AlertType.class)));
        watch.window().add(reading);
        if (watch.window().size() < minimumReadings) {
            return List.of();
        }
        var raised = new ArrayList<Breach>();
        for (var rule : rules) {
            var alreadyRaised = watch.active().get(rule.type());
            if (alreadyRaised != null && rule.cleared(watch.window())) {
                watch.active().remove(rule.type());
                continue;
            }
            rule.breach(watch.window())
                    .filter(breach -> alreadyRaised == null || breach.severity().compareTo(alreadyRaised) > 0)
                    .ifPresent(breach -> {
                        watch.active().put(rule.type(), breach.severity());
                        raised.add(breach);
                    });
        }
        return List.copyOf(raised);
    }

    /**
     * @param active the severity each raised and not yet cleared alert was last raised at
     */
    private record Watch(Window window, Map<AlertType, Severity> active) {
    }
}
