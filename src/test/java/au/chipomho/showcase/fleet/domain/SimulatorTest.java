package au.chipomho.showcase.fleet.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ticks are one second apart and each moves the trucks on four simulated seconds, as the service
 * runs by default.
 */
class SimulatorTest {

    private static final Instant START = Instant.parse("2026-10-20T10:00:00Z");
    private static final int TICKS = 3_000;

    private final Site site = Site.pilbaraNorthPit();
    private final Fleet fleet = Fleet.ofHaulTrucks(6);

    @Test
    void theSameSeedDrivesTheSameWay() {
        assertThat(run(42, 500)).isEqualTo(run(42, 500)).isNotEqualTo(run(43, 500));
    }

    @Test
    void everyTruckReportsOnEveryTickAtThatTime() {
        var ticks = run(1, 5);

        assertThat(ticks).hasSize(5).allSatisfy(readings ->
                assertThat(readings).extracting(Reading::truckId)
                        .containsExactly("HT-101", "HT-102", "HT-103", "HT-104", "HT-105", "HT-106"));
        assertThat(ticks.get(3)).extracting(Reading::recordedAt).containsOnly(START.plusSeconds(3));
    }

    @Test
    void drivesTheHaulCycleInOrder() {
        var statuses = new ArrayList<TruckStatus>();
        for (var readings : run(7, 600)) {
            var status = TruckState.statusOf(readings.getFirst(), site);
            if (statuses.isEmpty() || statuses.getLast() != status) {
                statuses.add(status);
            }
        }

        assertThat(statuses).startsWith(TruckStatus.LOADING, TruckStatus.HAULING, TruckStatus.DUMPING,
                TruckStatus.RETURNING, TruckStatus.LOADING);
    }

    @Test
    void staysOnSiteAndWithinWhatAHaulTruckCanDo() {
        assertThat(run(5, TICKS)).allSatisfy(readings -> assertThat(readings).allSatisfy(reading -> {
            assertThat(site.contains(reading.position())).isTrue();
            assertThat(reading.speedKph()).isBetween(0.0, 64.0);
            assertThat(reading.fuelPct()).isBetween(0.0, 100.0);
            assertThat(reading.payloadTonnes()).isBetween(0.0, 227 * 1.22);
            assertThat(reading.engineTempC()).isBetween(75.0, 125.0);
        }));
    }

    @Test
    void refuelsTrucksAtTheShovel() {
        var ticks = run(5, TICKS);
        var refuels = 0;
        for (var i = 1; i < ticks.size(); i++) {
            for (var truck = 0; truck < fleet.trucks().size(); truck++) {
                var before = ticks.get(i - 1).get(truck);
                var after = ticks.get(i).get(truck);
                if (after.fuelPct() > before.fuelPct()) {
                    refuels++;
                    assertThat(after.fuelPct()).isEqualTo(100.0);
                    assertThat(site.isIn(ZoneType.LOAD, after.position())).isTrue();
                }
            }
        }

        assertThat(refuels).isPositive();
    }

    @Test
    void givesEveryAlertRuleSomethingToFind() {
        var monitor = new AlertMonitor(AlertRule.standard(), Duration.ofSeconds(30), 3);
        var raised = EnumSet.noneOf(AlertType.class);
        var severities = EnumSet.noneOf(Severity.class);
        run(5, TICKS).forEach(readings -> readings.forEach(reading -> monitor.record(reading).forEach(breach -> {
            raised.add(breach.type());
            severities.add(breach.severity());
        })));

        assertThat(raised).containsExactlyInAnyOrder(AlertType.values());
        assertThat(severities).containsExactlyInAnyOrder(Severity.values());
    }

    private List<List<Reading>> run(long seed, int ticks) {
        var simulator = new Simulator(site, fleet, new SplittableRandom(seed), 4);
        return IntStream.range(0, ticks).mapToObj(tick -> simulator.tick(START.plusSeconds(tick))).toList();
    }
}
