package au.chipomho.showcase.fleet.web;

import au.chipomho.showcase.fleet.domain.Alert;
import au.chipomho.showcase.fleet.domain.Fleet;
import au.chipomho.showcase.fleet.domain.Reading;
import au.chipomho.showcase.fleet.domain.Site;
import au.chipomho.showcase.fleet.domain.TruckState;
import au.chipomho.showcase.fleet.telemetry.AlertRepository;
import au.chipomho.showcase.fleet.telemetry.TelemetryService;
import au.chipomho.showcase.fleet.telemetry.TruckStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
class FleetController {

    private final Site site;
    private final Fleet fleet;
    private final TruckStore trucks;
    private final AlertRepository alerts;
    private final TelemetryService telemetry;
    private final Clock clock;

    FleetController(Site site, Fleet fleet, TruckStore trucks, AlertRepository alerts, TelemetryService telemetry,
                    Clock clock) {
        this.site = site;
        this.fleet = fleet;
        this.trucks = trucks;
        this.alerts = alerts;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    @GetMapping("/site")
    Site site() {
        return site;
    }

    @GetMapping("/trucks")
    List<TruckState> trucks() {
        return trucks.all();
    }

    @GetMapping("/trucks/{truckId}")
    TruckState truck(@PathVariable String truckId) {
        requireInFleet(truckId);
        return trucks.find(truckId).orElseThrow(() -> Problems.notFound(truckId + " has not reported yet"));
    }

    @GetMapping("/trucks/{truckId}/readings")
    List<Reading> readings(@PathVariable String truckId, @RequestParam(defaultValue = "60") int limit) {
        requireBetween("limit", limit, 1, 300);
        requireInFleet(truckId);
        return trucks.readings(truckId, limit);
    }

    @GetMapping("/alerts")
    List<Alert> alerts(@RequestParam(defaultValue = "50") int limit, @RequestParam Optional<String> truckId) {
        requireBetween("limit", limit, 1, 200);
        return alerts.latest(limit, truckId);
    }

    @PostMapping(path = "/telemetry", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> telemetry(@RequestBody ReadingRequest request) {
        telemetry.ingest(request.toReading(fleet, site, clock.instant()));
        return ResponseEntity.accepted().build();
    }

    private void requireInFleet(String truckId) {
        if (fleet.find(truckId).isEmpty()) {
            throw Problems.notFound(truckId + " is not in the fleet");
        }
    }

    private static void requireBetween(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw Problems.badRequest("%s must be from %d to %d".formatted(name, min, max));
        }
    }
}
