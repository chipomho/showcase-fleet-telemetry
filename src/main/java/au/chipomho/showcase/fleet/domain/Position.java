package au.chipomho.showcase.fleet.domain;

/**
 * Where a truck is, in metres east (x) and north (y) of the site's south-west corner.
 */
public record Position(double x, double y) {

    public double distanceTo(Position other) {
        return Math.hypot(other.x - x, other.y - y);
    }

    /**
     * The compass bearing from here to the other position: 0 is north, 90 is east.
     */
    public double bearingTo(Position other) {
        var degrees = Math.toDegrees(Math.atan2(other.x - x, other.y - y));
        return Numbers.oneDecimal((degrees + 360) % 360) % 360;
    }
}
