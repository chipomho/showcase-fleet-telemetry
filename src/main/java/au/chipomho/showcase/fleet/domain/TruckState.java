package au.chipomho.showcase.fleet.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * What the dashboard shows for a truck: its latest reading plus what can be worked out from it.
 * A truck's telemetry unit reports neither its haul status nor its heading, so both are derived.
 */
public record TruckState(String truckId, String model, TruckStatus status, Position position, double headingDeg,
                         double speedKph, double engineTempC, double fuelPct, double payloadTonnes, Instant updatedAt) {

    static final double STOPPED_BELOW_KPH = 3;
    static final double LOADED_ABOVE_TONNES = 50;
    static final double MOVED_AT_LEAST_METRES = 1;

    public static TruckState of(Truck truck, Reading reading, Optional<TruckState> previous, Site site) {
        return new TruckState(truck.id(), truck.model(), statusOf(reading, site), reading.position(),
                headingOf(reading, previous), reading.speedKph(), reading.engineTempC(), reading.fuelPct(),
                reading.payloadTonnes(), reading.recordedAt());
    }

    /**
     * Stopped at the shovel is loading and stopped at the crusher is dumping. Anywhere else a
     * truck with a load is hauling and an empty one is returning.
     */
    static TruckStatus statusOf(Reading reading, Site site) {
        var stopped = reading.speedKph() < STOPPED_BELOW_KPH;
        if (stopped && site.isIn(ZoneType.LOAD, reading.position())) {
            return TruckStatus.LOADING;
        }
        if (stopped && site.isIn(ZoneType.DUMP, reading.position())) {
            return TruckStatus.DUMPING;
        }
        return reading.payloadTonnes() > LOADED_ABOVE_TONNES ? TruckStatus.HAULING : TruckStatus.RETURNING;
    }

    /**
     * The bearing of the last move. A truck that hasn't moved keeps the heading it had.
     */
    static double headingOf(Reading reading, Optional<TruckState> previous) {
        return previous
                .map(state -> state.position().distanceTo(reading.position()) >= MOVED_AT_LEAST_METRES
                        ? state.position().bearingTo(reading.position())
                        : state.headingDeg())
                .orElse(0.0);
    }
}
