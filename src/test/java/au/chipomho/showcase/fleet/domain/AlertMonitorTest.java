package au.chipomho.showcase.fleet.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AlertMonitorTest {

    private final AlertMonitor monitor = new AlertMonitor(AlertRule.standard(), Duration.ofSeconds(30), 3);

    @Test
    void waitsForEnoughReadingsBeforeJudging() {
        assertThat(feed(0, 2, second -> Sample.of("HT-101", second).temperature(120))).isEmpty();
        assertThat(feed(2, 3, second -> Sample.of("HT-101", second).temperature(120)))
                .extracting(Breach::type).containsExactly(AlertType.OVERHEATING);
    }

    @Test
    void raisesAnAlertOnceEscalatesItOnceAndRaisesItAgainOnlyAfterItHasCleared() {
        var hot = feed(0, 20, second -> Sample.of("HT-101", second).temperature(108));
        var hotter = feed(20, 40, second -> Sample.of("HT-101", second).temperature(115));
        var coolingButNotCool = feed(40, 50, second -> Sample.of("HT-101", second).temperature(103));
        var cooled = feed(50, 90, second -> Sample.of("HT-101", second).temperature(92));
        var hotAgain = feed(90, 130, second -> Sample.of("HT-101", second).temperature(109));

        assertThat(hot).containsExactly(new Breach(AlertType.OVERHEATING, Severity.WARNING,
                "Engine temperature 108.0°C is above 105°C"));
        assertThat(hotter).extracting(Breach::type, Breach::severity)
                .containsExactly(tuple(AlertType.OVERHEATING, Severity.CRITICAL));
        assertThat(coolingButNotCool).isEmpty();
        assertThat(cooled).isEmpty();
        assertThat(hotAgain).extracting(Breach::type, Breach::severity)
                .containsExactly(tuple(AlertType.OVERHEATING, Severity.WARNING));
    }

    @Test
    void judgesTheMeanOfTheLastThirtySecondsNotOneReading() {
        feed(0, 30, second -> Sample.of("HT-101", second).speed(40));

        assertThat(feed(30, 31, second -> Sample.of("HT-101", second).speed(90))).isEmpty();
        assertThat(feed(31, 61, second -> Sample.of("HT-101", second).speed(58)))
                .extracting(Breach::type).containsExactly(AlertType.OVERSPEED);
    }

    @Test
    void keepsEachTrucksWindowSeparate() {
        feed(0, 5, second -> Sample.of("HT-101", second).fuel(10));

        assertThat(feed(0, 5, second -> Sample.of("HT-102", second).fuel(10)))
                .extracting(Breach::type).containsExactly(AlertType.LOW_FUEL);
    }

    @Test
    void raisesSeveralAlertsFromOneReadingWhenSeveralThingsAreWrong() {
        assertThat(feed(0, 3, second -> Sample.of("HT-101", second).temperature(111).fuel(3).speed(62).payload(275)))
                .extracting(Breach::type, Breach::severity)
                .containsExactly(
                        tuple(AlertType.OVERHEATING, Severity.CRITICAL),
                        tuple(AlertType.LOW_FUEL, Severity.CRITICAL),
                        tuple(AlertType.OVERSPEED, Severity.CRITICAL),
                        tuple(AlertType.OVERLOAD, Severity.CRITICAL));
    }

    private List<Breach> feed(int fromSecond, int toSecond, IntFunction<Sample> sample) {
        var raised = new ArrayList<Breach>();
        IntStream.range(fromSecond, toSecond).forEach(second -> raised.addAll(monitor.record(sample.apply(second).reading())));
        return raised;
    }
}
