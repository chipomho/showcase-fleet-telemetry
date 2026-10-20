package au.chipomho.showcase.fleet.simulation;

import au.chipomho.showcase.fleet.FleetProperties;
import au.chipomho.showcase.fleet.domain.Simulator;
import au.chipomho.showcase.fleet.telemetry.TelemetryService;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;

/**
 * Runs the simulator on a scheduler, when it is enabled, feeding its readings in through the same
 * service that takes them over HTTP. Tests give it a scheduler they can step by hand.
 */
@Component
public class SimulatorRunner implements SmartLifecycle {

    private final Simulator simulator;
    private final TelemetryService telemetry;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final FleetProperties.Simulator settings;
    private ScheduledFuture<?> ticking;

    public SimulatorRunner(Simulator simulator, TelemetryService telemetry, TaskScheduler scheduler, Clock clock,
                           FleetProperties properties) {
        this.simulator = simulator;
        this.telemetry = telemetry;
        this.scheduler = scheduler;
        this.clock = clock;
        this.settings = properties.simulator();
    }

    @Override
    public synchronized void start() {
        if (settings.enabled()) {
            ticking = scheduler.scheduleAtFixedRate(this::tick, settings.tick());
        }
    }

    @Override
    public synchronized void stop() {
        if (ticking != null) {
            ticking.cancel(false);
            ticking = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return ticking != null;
    }

    /**
     * One step for the whole fleet, stamped with the current time.
     */
    public synchronized void tick() {
        simulator.tick(clock.instant().truncatedTo(ChronoUnit.MILLIS)).forEach(telemetry::ingest);
    }
}
