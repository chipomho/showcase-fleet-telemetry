package au.chipomho.showcase.fleet.domain;

import java.util.List;

/**
 * The haul loop measured in metres from the loading point. Distances wrap, so the loop can be
 * driven round and round.
 */
public final class HaulRoute {

    private final List<Position> points;
    private final double[] startOf;
    private final double length;

    public HaulRoute(List<Point> route) {
        if (route.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two points");
        }
        this.points = route.stream().map(point -> new Position(point.x(), point.y())).toList();
        this.startOf = new double[points.size() + 1];
        for (var i = 0; i < points.size(); i++) {
            startOf[i + 1] = startOf[i] + points.get(i).distanceTo(next(i));
        }
        this.length = startOf[points.size()];
    }

    public double length() {
        return length;
    }

    public Position positionAt(double distance) {
        var along = wrap(distance);
        var segment = segmentAt(along);
        var from = points.get(segment);
        var to = next(segment);
        var fraction = (along - startOf[segment]) / (startOf[segment + 1] - startOf[segment]);
        return new Position(from.x() + (to.x() - from.x()) * fraction, from.y() + (to.y() - from.y()) * fraction);
    }

    /**
     * How far along the loop the point nearest to the given position is.
     */
    public double distanceToPointNearest(Position position) {
        var nearest = 0;
        for (var i = 1; i < points.size(); i++) {
            if (points.get(i).distanceTo(position) < points.get(nearest).distanceTo(position)) {
                nearest = i;
            }
        }
        return startOf[nearest];
    }

    private double wrap(double distance) {
        return (distance % length + length) % length;
    }

    private int segmentAt(double along) {
        var segment = 0;
        while (startOf[segment + 1] <= along) {
            segment++;
        }
        return segment;
    }

    private Position next(int index) {
        return points.get((index + 1) % points.size());
    }
}
