package au.chipomho.showcase.fleet.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class GeometryTest {

    private final Site site = Site.pilbaraNorthPit();

    /**
     * A 100 x 50 rectangle driven anticlockwise from the origin: 300 m round.
     */
    private final HaulRoute rectangle = new HaulRoute(List.of(
            new Point(0, 0), new Point(100, 0), new Point(100, 50), new Point(0, 50)));

    @ParameterizedTest(name = "{0},{1} -> {2},{3} is {4}")
    @CsvSource({
            "0, 0, 0, 10, 0.0",
            "0, 0, 10, 0, 90.0",
            "0, 0, 0, -10, 180.0",
            "0, 0, -10, 0, 270.0",
            "0, 0, 10, 10, 45.0",
            "0, 0, -0.001, 10, 0.0"
    })
    void bearingsAreCompassDegrees(double x1, double y1, double x2, double y2, double bearing) {
        assertThat(new Position(x1, y1).bearingTo(new Position(x2, y2))).isEqualTo(bearing);
    }

    @Test
    void measuresTheLoopIncludingTheLegBackToTheStart() {
        assertThat(rectangle.length()).isEqualTo(300);
    }

    @ParameterizedTest(name = "{0} m along is {1},{2}")
    @CsvSource({
            "0, 0, 0",
            "40, 40, 0",
            "125, 100, 25",
            "275, 0, 25",
            "340, 40, 0",
            "-25, 0, 25"
    })
    void findsPositionsAlongTheLoopAndWrapsRound(double distance, double x, double y) {
        var position = rectangle.positionAt(distance);

        assertThat(position.x()).isCloseTo(x, within(1e-9));
        assertThat(position.y()).isCloseTo(y, within(1e-9));
    }

    @Test
    void findsHowFarAlongTheNearestPointIs() {
        assertThat(rectangle.distanceToPointNearest(new Position(95, 45))).isEqualTo(150);
        assertThat(rectangle.distanceToPointNearest(new Position(-5, 2))).isEqualTo(0);
    }

    @Test
    void needsTwoPointsForARoute() {
        assertThatIllegalArgumentException().isThrownBy(() -> new HaulRoute(List.of(new Point(1, 1))));
    }

    @ParameterizedTest(name = "{0},{1}")
    @CsvSource({"0, 0, true", "4000, 3000, true", "-1, 10, false", "4001, 10, false", "10, -1, false", "10, 3001, false"})
    void knowsWhatIsOnSite(double x, double y, boolean inside) {
        assertThat(site.contains(new Position(x, y))).isEqualTo(inside);
    }

    @Test
    void knowsWhenATruckIsInAZone() {
        assertThat(site.isIn(ZoneType.LOAD, new Position(350, 420))).isTrue();
        assertThat(site.isIn(ZoneType.LOAD, new Position(3480, 2580))).isFalse();
        assertThat(site.isIn(ZoneType.DUMP, new Position(3480, 2580))).isTrue();
        assertThat(site.isIn(ZoneType.DUMP, new Position(3500, 2751))).isFalse();
        assertThat(site.zone(ZoneType.DUMP).name()).isEqualTo("Crusher");
    }

    @Test
    void namesTheFleetInOrder() {
        var fleet = Fleet.ofHaulTrucks(3);

        assertThat(fleet.trucks()).containsExactly(
                new Truck("HT-101", "Cat 793F"), new Truck("HT-102", "Cat 793F"), new Truck("HT-103", "Cat 793F"));
        assertThat(fleet.find("HT-102")).contains(new Truck("HT-102", "Cat 793F"));
        assertThat(fleet.find("HT-104")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0", "900"})
    void refusesAnUnreasonableFleetSize(int count) {
        assertThatIllegalArgumentException().isThrownBy(() -> Fleet.ofHaulTrucks(count))
                .withMessage("A fleet has from 1 to 899 trucks, not " + count);
    }
}
