package au.chipomho.showcase.fleet.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Drives the fleet round the haul loop: load at the shovel, haul to the crusher, dump, return
 * empty. Each tick moves every truck on by a fixed number of simulated seconds and produces the
 * reading its telemetry unit would send, nothing more; status and alerts are worked out by
 * whoever consumes the readings, exactly as they would be for a real truck.
 *
 * <p>Some cycles go wrong on purpose so the alert rules have something to find: a cooling fault on
 * the haul, a heavy foot on the way back, an overloaded tray. Fuel runs down over several cycles
 * and is topped up at the shovel. Given the same seed and the same tick times, the readings are
 * always the same. Not thread-safe.
 */
public final class Simulator {

    static final double RATED_PAYLOAD_TONNES = 227;
    static final double LOADING_SECONDS = 120;
    static final double DUMPING_SECONDS = 60;
    static final double REFUEL_BELOW_PCT = 20;

    private final HaulRoute route;
    private final double dumpPoint;
    private final RandomGenerator random;
    private final double secondsPerTick;
    private final List<DrivenTruck> trucks = new ArrayList<>();

    /**
     * @param secondsPerTick how far each tick moves the trucks on, in simulated seconds
     */
    public Simulator(Site site, Fleet fleet, RandomGenerator random, double secondsPerTick) {
        this.route = new HaulRoute(site.route());
        this.dumpPoint = route.distanceToPointNearest(site.zone(ZoneType.DUMP).position());
        this.random = random;
        this.secondsPerTick = secondsPerTick;
        var spacing = route.length() / fleet.trucks().size();
        for (var i = 0; i < fleet.trucks().size(); i++) {
            trucks.add(new DrivenTruck(fleet.trucks().get(i).id(), i * spacing));
        }
    }

    public List<Reading> tick(Instant now) {
        var readings = new ArrayList<Reading>(trucks.size());
        for (var truck : trucks) {
            truck.advance(secondsPerTick);
            readings.add(truck.reading(now));
        }
        return List.copyOf(readings);
    }

    private final class DrivenTruck {

        private final String id;
        private TruckStatus phase;
        private double distance;
        private double payload;
        private double targetPayload;
        private double fuel;
        private double engineTemp = 88;
        private double cruiseKph;
        private boolean coolingFault;

        /**
         * Starts the truck part way round the loop, loaded on the way out and empty on the way back.
         */
        DrivenTruck(String id, double distance) {
            this.id = id;
            this.distance = distance;
            this.fuel = 25 + random.nextDouble() * 75;
            if (distance == 0) {
                phase = startLoading();
            } else if (distance < dumpPoint) {
                targetPayload = RATED_PAYLOAD_TONNES;
                payload = targetPayload;
                phase = startHaul();
            } else {
                phase = startReturn();
            }
        }

        void advance(double seconds) {
            phase = switch (phase) {
                case LOADING -> load(seconds);
                case HAULING -> haul(seconds);
                case DUMPING -> dump(seconds);
                case RETURNING -> driveBack(seconds);
            };
            fuel = Math.max(0, fuel - burnPerSecond() * seconds);
            engineTemp += (targetEngineTemp() - engineTemp) * Math.min(1, seconds / 90);
        }

        private TruckStatus load(double seconds) {
            payload = Math.min(targetPayload, payload + targetPayload * seconds / LOADING_SECONDS);
            return payload == targetPayload ? startHaul() : TruckStatus.LOADING;
        }

        private TruckStatus haul(double seconds) {
            distance = Math.min(dumpPoint, distance + metresIn(seconds));
            return distance == dumpPoint ? TruckStatus.DUMPING : TruckStatus.HAULING;
        }

        private TruckStatus dump(double seconds) {
            payload = Math.max(0, payload - targetPayload * seconds / DUMPING_SECONDS);
            return payload == 0 ? startReturn() : TruckStatus.DUMPING;
        }

        private TruckStatus driveBack(double seconds) {
            distance = Math.min(route.length(), distance + metresIn(seconds));
            return distance == route.length() ? startLoading() : TruckStatus.RETURNING;
        }

        Reading reading(Instant now) {
            var position = route.positionAt(distance);
            return new Reading(id, now, new Position(Numbers.oneDecimal(position.x()), Numbers.oneDecimal(position.y())),
                    Numbers.oneDecimal(speedKph()), Numbers.oneDecimal(engineTemp), Numbers.oneDecimal(fuel),
                    Numbers.oneDecimal(payload));
        }

        /**
         * A tenth of loads are 12 to 22 per cent over the rated payload. Trucks are refuelled
         * while they load.
         */
        private TruckStatus startLoading() {
            distance = 0;
            payload = 0;
            targetPayload = RATED_PAYLOAD_TONNES * (random.nextDouble() < 0.1
                    ? 1.12 + random.nextDouble() * 0.1
                    : 0.94 + random.nextDouble() * 0.1);
            if (fuel < REFUEL_BELOW_PCT) {
                fuel = 100;
            }
            return TruckStatus.LOADING;
        }

        /**
         * Loaded trucks cruise at 38 to 44 km/h; one haul in ten has a cooling fault.
         */
        private TruckStatus startHaul() {
            cruiseKph = 38 + random.nextDouble() * 6;
            coolingFault = random.nextDouble() < 0.1;
            return TruckStatus.HAULING;
        }

        /**
         * Empty trucks cruise at 46 to 52 km/h, except one return in five at 57 to 64.
         */
        private TruckStatus startReturn() {
            cruiseKph = random.nextDouble() < 0.2 ? 57 + random.nextDouble() * 7 : 46 + random.nextDouble() * 6;
            coolingFault = false;
            return TruckStatus.RETURNING;
        }

        private boolean moving() {
            return phase == TruckStatus.HAULING || phase == TruckStatus.RETURNING;
        }

        private double speedKph() {
            return moving() ? cruiseKph : 0;
        }

        private double metresIn(double seconds) {
            return cruiseKph / 3.6 * seconds;
        }

        /**
         * Per cent of the tank per second: most when hauling a load, least when standing.
         */
        private double burnPerSecond() {
            if (!moving()) {
                return 0.004;
            }
            return payload > 0 ? 0.02 : 0.012;
        }

        private double targetEngineTemp() {
            var working = moving() ? 84 + 10 * payload / RATED_PAYLOAD_TONNES : 80;
            return coolingFault ? working + 30 : working;
        }
    }
}
