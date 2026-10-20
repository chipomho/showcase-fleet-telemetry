package au.chipomho.showcase.fleet.telemetry;

import au.chipomho.showcase.fleet.domain.AlertMonitor;
import au.chipomho.showcase.fleet.domain.Fleet;
import au.chipomho.showcase.fleet.domain.Reading;
import au.chipomho.showcase.fleet.domain.Site;
import au.chipomho.showcase.fleet.domain.Truck;
import au.chipomho.showcase.fleet.domain.TruckState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * The one way readings come in, whether from the simulator or over HTTP. Each reading updates its
 * truck's state, is checked against the alert rules, and is announced to the live feed.
 */
@Service
public class TelemetryService {

    private final Fleet fleet;
    private final Site site;
    private final TruckStore trucks;
    private final AlertRepository alerts;
    private final AlertMonitor monitor;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Map<String, ReentrantLock> locks;

    TelemetryService(Fleet fleet, Site site, TruckStore trucks, AlertRepository alerts, AlertMonitor monitor,
                     ApplicationEventPublisher events, Clock clock) {
        this.fleet = fleet;
        this.site = site;
        this.trucks = trucks;
        this.alerts = alerts;
        this.monitor = monitor;
        this.events = events;
        this.clock = clock;
        this.locks = fleet.trucks().stream()
                .collect(Collectors.toUnmodifiableMap(Truck::id, _ -> new ReentrantLock()));
    }

    /**
     * Takes a reading for a truck in the fleet. A reading no newer than the truck's current state
     * arrived out of order and is dropped, so a truck never moves back in time.
     */
    public void ingest(Reading reading) {
        var truck = fleet.find(reading.truckId())
                .orElseThrow(() -> new IllegalArgumentException(reading.truckId() + " is not in the fleet"));
        var lock = locks.get(truck.id());
        lock.lock();
        try {
            var previous = trucks.find(truck.id());
            if (previous.map(state -> !reading.recordedAt().isAfter(state.updatedAt())).orElse(false)) {
                return;
            }
            var state = TruckState.of(truck, reading, previous, site);
            trucks.save(state, reading);
            events.publishEvent(new FleetEvent.TruckUpdated(state));
            for (var breach : monitor.record(reading)) {
                var alert = alerts.save(truck.id(), breach, clock.instant().truncatedTo(ChronoUnit.MILLIS));
                events.publishEvent(new FleetEvent.AlertRaised(alert));
            }
        } finally {
            lock.unlock();
        }
    }
}
