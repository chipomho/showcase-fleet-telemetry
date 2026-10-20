package au.chipomho.showcase.fleet.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A condition worth telling someone about, judged on the mean of a reading over a truck's recent
 * window rather than on any single reading. Each rule raises at one threshold and clears at a
 * safer one, so a value hovering near the limit doesn't raise the alert over and over.
 */
public sealed interface AlertRule permits AlertRule.Overheating, AlertRule.LowFuel, AlertRule.Overspeed,
        AlertRule.Overload {

    AlertType type();

    /**
     * The breach the window shows, if any.
     */
    Optional<Breach> breach(Window window);

    /**
     * Whether a raised alert of this type is over.
     */
    boolean cleared(Window window);

    /**
     * Limits for a Cat 793F (227 t rated payload) on a site with a 55 km/h haul road limit.
     */
    static List<AlertRule> standard() {
        return List.of(
                new Overheating(105, 110, 100),
                new LowFuel(15, 5, 20),
                new Overspeed(55, 60, 52),
                new Overload(250, 272, 240));
    }

    record Overheating(double warnAboveC, double criticalAboveC, double clearBelowC) implements AlertRule {

        @Override
        public AlertType type() {
            return AlertType.OVERHEATING;
        }

        @Override
        public Optional<Breach> breach(Window window) {
            var temperature = window.mean(Reading::engineTempC);
            return temperature > warnAboveC
                    ? Optional.of(new Breach(type(), temperature > criticalAboveC ? Severity.CRITICAL : Severity.WARNING,
                    format("Engine temperature %.1f°C is above %.0f°C", temperature, warnAboveC)))
                    : Optional.empty();
        }

        @Override
        public boolean cleared(Window window) {
            return window.mean(Reading::engineTempC) < clearBelowC;
        }
    }

    record LowFuel(double warnBelowPct, double criticalBelowPct, double clearAbovePct) implements AlertRule {

        @Override
        public AlertType type() {
            return AlertType.LOW_FUEL;
        }

        @Override
        public Optional<Breach> breach(Window window) {
            var fuel = window.mean(Reading::fuelPct);
            return fuel < warnBelowPct
                    ? Optional.of(new Breach(type(), fuel < criticalBelowPct ? Severity.CRITICAL : Severity.WARNING,
                    format("Fuel at %.1f%% is below %.0f%%", fuel, warnBelowPct)))
                    : Optional.empty();
        }

        @Override
        public boolean cleared(Window window) {
            return window.mean(Reading::fuelPct) > clearAbovePct;
        }
    }

    record Overspeed(double warnAboveKph, double criticalAboveKph, double clearBelowKph) implements AlertRule {

        @Override
        public AlertType type() {
            return AlertType.OVERSPEED;
        }

        @Override
        public Optional<Breach> breach(Window window) {
            var speed = window.mean(Reading::speedKph);
            return speed > warnAboveKph
                    ? Optional.of(new Breach(type(), speed > criticalAboveKph ? Severity.CRITICAL : Severity.WARNING,
                    format("Speed %.1f km/h is above %.0f km/h", speed, warnAboveKph)))
                    : Optional.empty();
        }

        @Override
        public boolean cleared(Window window) {
            return window.mean(Reading::speedKph) < clearBelowKph;
        }
    }

    record Overload(double warnAboveTonnes, double criticalAboveTonnes, double clearBelowTonnes) implements AlertRule {

        @Override
        public AlertType type() {
            return AlertType.OVERLOAD;
        }

        @Override
        public Optional<Breach> breach(Window window) {
            var payload = window.mean(Reading::payloadTonnes);
            return payload > warnAboveTonnes
                    ? Optional.of(new Breach(type(), payload > criticalAboveTonnes ? Severity.CRITICAL : Severity.WARNING,
                    format("Payload %.1f t is above %.0f t", payload, warnAboveTonnes)))
                    : Optional.empty();
        }

        @Override
        public boolean cleared(Window window) {
            return window.mean(Reading::payloadTonnes) < clearBelowTonnes;
        }
    }

    private static String format(String pattern, double value, double limit) {
        return String.format(Locale.ROOT, pattern, value, limit);
    }
}
