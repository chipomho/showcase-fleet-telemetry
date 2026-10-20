package au.chipomho.showcase.fleet.domain;

/**
 * A place where trucks stop: a shovel they load at, or a crusher they dump into.
 */
public record Zone(String name, ZoneType type, int x, int y) {

    public Position position() {
        return new Position(x, y);
    }
}
