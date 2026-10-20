package au.chipomho.showcase.fleet.web;

import au.chipomho.showcase.fleet.domain.Fleet;
import au.chipomho.showcase.fleet.domain.Position;
import au.chipomho.showcase.fleet.domain.Reading;
import au.chipomho.showcase.fleet.domain.Site;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A reading as posted. Every field is optional here so that all the problems with a body can be
 * reported together rather than one at a time.
 */
record ReadingRequest(@Nullable String truckId, @Nullable Instant recordedAt, @Nullable Coordinates position,
                      @Nullable Double speedKph, @Nullable Double engineTempC, @Nullable Double fuelPct,
                      @Nullable Double payloadTonnes) {

    /**
     * How far ahead of the server's clock a truck's clock may run.
     */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    record Coordinates(@Nullable Double x, @Nullable Double y) {
    }

    Reading toReading(Fleet fleet, Site site, Instant now) {
        var problems = new ArrayList<String>();
        if (truckId == null) {
            problems.add("truckId is required");
        } else if (fleet.find(truckId).isEmpty()) {
            problems.add(truckId + " is not in the fleet");
        }
        if (recordedAt == null) {
            problems.add("recordedAt is required");
        } else if (recordedAt.isAfter(now.plus(CLOCK_SKEW))) {
            problems.add("recordedAt is in the future");
        }
        if (position == null || position.x() == null || position.y() == null) {
            problems.add("position needs x and y");
        } else if (!site.contains(new Position(position.x(), position.y()))) {
            problems.add("position is outside the site");
        }
        checkRange(problems, "speedKph", speedKph, 0, 150);
        checkRange(problems, "engineTempC", engineTempC, -40, 200);
        checkRange(problems, "fuelPct", fuelPct, 0, 100);
        checkRange(problems, "payloadTonnes", payloadTonnes, 0, 400);
        if (!problems.isEmpty()) {
            throw Problems.badRequest(String.join("; ", problems));
        }
        return new Reading(truckId, recordedAt, new Position(position.x(), position.y()), speedKph, engineTempC,
                fuelPct, payloadTonnes);
    }

    private static void checkRange(List<String> problems, String name, @Nullable Double value, double min, double max) {
        if (value == null) {
            problems.add(name + " is required");
        } else if (value < min || value > max) {
            problems.add("%s must be from %s to %s".formatted(name, (int) min, (int) max));
        }
    }
}
