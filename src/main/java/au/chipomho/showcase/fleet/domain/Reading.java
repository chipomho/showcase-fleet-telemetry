package au.chipomho.showcase.fleet.domain;

import java.time.Instant;

/**
 * One telemetry sample from a truck.
 */
public record Reading(String truckId, Instant recordedAt, Position position, double speedKph, double engineTempC,
                      double fuelPct, double payloadTonnes) {
}
