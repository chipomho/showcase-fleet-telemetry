package au.chipomho.showcase.fleet.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleTest {

    @ParameterizedTest(name = "{0} at {1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            OVERHEATING | 105   |          |
            OVERHEATING | 105.1 | WARNING  | Engine temperature 105.1°C is above 105°C
            OVERHEATING | 112.4 | CRITICAL | Engine temperature 112.4°C is above 105°C
            LOW_FUEL    | 15    |          |
            LOW_FUEL    | 12    | WARNING  | Fuel at 12.0% is below 15%
            LOW_FUEL    | 4.5   | CRITICAL | Fuel at 4.5% is below 15%
            OVERSPEED   | 55    |          |
            OVERSPEED   | 58.3  | WARNING  | Speed 58.3 km/h is above 55 km/h
            OVERSPEED   | 61    | CRITICAL | Speed 61.0 km/h is above 55 km/h
            OVERLOAD    | 250   |          |
            OVERLOAD    | 255   | WARNING  | Payload 255.0 t is above 250 t
            OVERLOAD    | 273   | CRITICAL | Payload 273.0 t is above 250 t
            """)
    void raisesAtTheWarningLimitAndEscalatesAtTheCriticalOne(AlertType type, double value, Severity severity,
                                                              String message) {
        var breach = rule(type).breach(windowOf(type, value));

        if (severity == null) {
            assertThat(breach).isEmpty();
        } else {
            assertThat(breach).contains(new Breach(type, severity, message));
        }
    }

    @ParameterizedTest(name = "{0} at {1} cleared: {2}")
    @CsvSource({
            "OVERHEATING, 100, false", "OVERHEATING, 99.9, true",
            "LOW_FUEL, 20, false", "LOW_FUEL, 20.1, true",
            "OVERSPEED, 52, false", "OVERSPEED, 51.9, true",
            "OVERLOAD, 240, false", "OVERLOAD, 0, true"
    })
    void clearsOnlyOnceSafelyBackInsideTheLimit(AlertType type, double value, boolean cleared) {
        assertThat(rule(type).cleared(windowOf(type, value))).isEqualTo(cleared);
    }

    private static AlertRule rule(AlertType type) {
        return AlertRule.standard().stream().filter(rule -> rule.type() == type).findFirst().orElseThrow();
    }

    private static Window windowOf(AlertType type, double value) {
        UnaryOperator<Sample> set = switch (type) {
            case OVERHEATING -> sample -> sample.temperature(value);
            case LOW_FUEL -> sample -> sample.fuel(value);
            case OVERSPEED -> sample -> sample.speed(value);
            case OVERLOAD -> sample -> sample.payload(value);
        };
        var window = new Window(Duration.ofSeconds(30));
        window.add(set.apply(Sample.of("HT-101", 0)).reading());
        return window;
    }
}
