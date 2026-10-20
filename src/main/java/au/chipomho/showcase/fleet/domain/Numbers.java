package au.chipomho.showcase.fleet.domain;

final class Numbers {

    private Numbers() {
    }

    static double oneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
