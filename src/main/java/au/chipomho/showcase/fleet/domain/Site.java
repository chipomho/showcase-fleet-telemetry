package au.chipomho.showcase.fleet.domain;

import java.util.List;

/**
 * The mine site: its extent, the haul loop trucks drive, and the zones they stop at. The route
 * is a closed loop; its last point joins back to the first, which is the loading point.
 */
public record Site(String name, int width, int height, List<Point> route, List<Zone> zones) {

    /**
     * How close to a zone's marker a truck has to be to count as being in it.
     */
    static final double ZONE_RADIUS_METRES = 150;

    public Site {
        route = List.copyOf(route);
        zones = List.copyOf(zones);
    }

    public static Site pilbaraNorthPit() {
        return new Site("Pilbara North Pit", 4000, 3000,
                List.of(
                        new Point(350, 420), new Point(700, 520), new Point(1150, 700), new Point(1650, 980),
                        new Point(2150, 1350), new Point(2650, 1800), new Point(3100, 2250), new Point(3480, 2580),
                        new Point(3350, 2700), new Point(2950, 2420), new Point(2450, 1980), new Point(1950, 1560),
                        new Point(1450, 1180), new Point(950, 860), new Point(550, 620)),
                List.of(
                        new Zone("Shovel 1", ZoneType.LOAD, 400, 450),
                        new Zone("Crusher", ZoneType.DUMP, 3500, 2600)));
    }

    public boolean contains(Position position) {
        return position.x() >= 0 && position.x() <= width && position.y() >= 0 && position.y() <= height;
    }

    public boolean isIn(ZoneType type, Position position) {
        return zones.stream()
                .filter(zone -> zone.type() == type)
                .anyMatch(zone -> zone.position().distanceTo(position) <= ZONE_RADIUS_METRES);
    }

    public Zone zone(ZoneType type) {
        return zones.stream().filter(zone -> zone.type() == type).findFirst().orElseThrow();
    }
}
