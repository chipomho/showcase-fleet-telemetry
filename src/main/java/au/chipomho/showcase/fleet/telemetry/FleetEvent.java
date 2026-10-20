package au.chipomho.showcase.fleet.telemetry;

import au.chipomho.showcase.fleet.domain.Alert;
import au.chipomho.showcase.fleet.domain.TruckState;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * What the live feed sends. The service publishes updates and alerts as application events, and
 * each one goes out as a JSON frame whose {@code type} names the record.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FleetEvent.Snapshot.class, name = "SNAPSHOT"),
        @JsonSubTypes.Type(value = FleetEvent.TruckUpdated.class, name = "TRUCK_UPDATED"),
        @JsonSubTypes.Type(value = FleetEvent.AlertRaised.class, name = "ALERT_RAISED")
})
public sealed interface FleetEvent {

    record Snapshot(List<TruckState> trucks) implements FleetEvent {
    }

    record TruckUpdated(TruckState truck) implements FleetEvent {
    }

    record AlertRaised(Alert alert) implements FleetEvent {
    }
}
