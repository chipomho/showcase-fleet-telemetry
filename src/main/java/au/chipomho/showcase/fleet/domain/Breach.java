package au.chipomho.showcase.fleet.domain;

/**
 * A rule's finding that its condition has started: an alert that has not been stored yet.
 */
public record Breach(AlertType type, Severity severity, String message) {
}
