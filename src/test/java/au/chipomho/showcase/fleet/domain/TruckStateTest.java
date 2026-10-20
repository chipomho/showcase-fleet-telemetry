package au.chipomho.showcase.fleet.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TruckStateTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:15:30Z");
    private final Site site = Site.pilbaraNorthPit();
    private final Truck truck = new Truck("HT-101", "Cat 793F");

    @ParameterizedTest(name = "at {0},{1} doing {2} km/h with {3} t: {4}")
    @CsvSource({
            "400, 450, 0, 120, LOADING",
            "400, 450, 2.9, 0, LOADING",
            "400, 450, 30, 0, RETURNING",
            "3500, 2600, 0, 220, DUMPING",
            "3500, 2600, 35, 220, HAULING",
            "2000, 1500, 0, 220, HAULING",
            "2000, 1500, 0, 50, RETURNING",
            "2000, 1500, 48, 0, RETURNING"
    })
    void worksOutWhereTheTruckIsInItsCycle(double x, double y, double speed, double payload, TruckStatus status) {
        var reading = new Reading("HT-101", NOW, new Position(x, y), speed, 90, 60, payload);

        assertThat(TruckState.of(truck, reading, Optional.empty(), site).status()).isEqualTo(status);
    }

    @Test
    void takesEverythingElseFromTheReading() {
        var reading = new Reading("HT-101", NOW, new Position(1234.5, 567.8), 38.2, 96.4, 61.0, 221.5);

        assertThat(TruckState.of(truck, reading, Optional.empty(), site)).isEqualTo(new TruckState("HT-101", "Cat 793F",
                TruckStatus.HAULING, new Position(1234.5, 567.8), 0.0, 38.2, 96.4, 61.0, 221.5, NOW));
    }

    @Test
    void headsTheWayItLastMovedAndKeepsThatHeadingWhileStopped() {
        var first = TruckState.of(truck, reading(1000, 1000), Optional.empty(), site);
        var movedEast = TruckState.of(truck, reading(1010, 1000), Optional.of(first), site);
        var barelyMoved = TruckState.of(truck, reading(1010.5, 1000.5), Optional.of(movedEast), site);

        assertThat(first.headingDeg()).isEqualTo(0.0);
        assertThat(movedEast.headingDeg()).isEqualTo(90.0);
        assertThat(barelyMoved.headingDeg()).isEqualTo(90.0);
    }

    private static Reading reading(double x, double y) {
        return new Reading("HT-101", NOW, new Position(x, y), 30, 90, 60, 0);
    }
}
