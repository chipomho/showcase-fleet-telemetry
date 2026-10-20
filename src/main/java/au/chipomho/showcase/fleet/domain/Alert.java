package au.chipomho.showcase.fleet.domain;

import java.time.Instant;

public record Alert(long id, String truckId, AlertType type, Severity severity, String message, Instant raisedAt) {
}
