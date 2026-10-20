package au.chipomho.showcase.fleet.domain;

import java.time.Instant;

/**
 * A reading built up in tests: an unremarkable empty truck on the road unless told otherwise.
 */
record Sample(String truckId, int second, double speedKph, double engineTempC, double fuelPct, double payloadTonnes) {

    static final Instant START = Instant.parse("2026-10-20T10:00:00Z");

    static Sample of(String truckId, int second) {
        return new Sample(truckId, second, 40, 90, 60, 0);
    }

    Sample speed(double kph) {
        return new Sample(truckId, second, kph, engineTempC, fuelPct, payloadTonnes);
    }

    Sample temperature(double celsius) {
        return new Sample(truckId, second, speedKph, celsius, fuelPct, payloadTonnes);
    }

    Sample fuel(double pct) {
        return new Sample(truckId, second, speedKph, engineTempC, pct, payloadTonnes);
    }

    Sample payload(double tonnes) {
        return new Sample(truckId, second, speedKph, engineTempC, fuelPct, tonnes);
    }

    Reading reading() {
        return new Reading(truckId, START.plusSeconds(second), new Position(2000, 1500), speedKph, engineTempC, fuelPct,
                payloadTonnes);
    }
}
