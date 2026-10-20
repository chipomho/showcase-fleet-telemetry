package au.chipomho.showcase.fleet.web;

import au.chipomho.showcase.fleet.IntegrationTest;
import au.chipomho.showcase.fleet.domain.Position;
import au.chipomho.showcase.fleet.domain.Reading;
import au.chipomho.showcase.fleet.simulation.SimulatorRunner;
import au.chipomho.showcase.fleet.telemetry.TelemetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FleetApiIntegrationTest extends IntegrationTest {

    @Autowired
    private SimulatorRunner simulator;

    @Autowired
    private TelemetryService telemetry;

    @Test
    void describesTheSite() {
        client.get().uri("/v1/site").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("""
                        {"name":"Pilbara North Pit","width":4000,"height":3000,"route":[{"x":350,"y":420},\
                        {"x":700,"y":520},{"x":1150,"y":700},{"x":1650,"y":980},{"x":2150,"y":1350},{"x":2650,"y":1800},\
                        {"x":3100,"y":2250},{"x":3480,"y":2580},{"x":3350,"y":2700},{"x":2950,"y":2420},\
                        {"x":2450,"y":1980},{"x":1950,"y":1560},{"x":1450,"y":1180},{"x":950,"y":860},{"x":550,"y":620}],\
                        "zones":[{"name":"Shovel 1","type":"LOAD","x":400,"y":450},\
                        {"name":"Crusher","type":"DUMP","x":3500,"y":2600}]}""");
    }

    @Test
    void acceptsAReadingAndShowsTheTruckItDescribes() {
        var at = clock.advance(Duration.ofSeconds(1));
        post("""
                {"truckId":"HT-101","recordedAt":"%s","position":{"x":1234.5,"y":567.8},"speedKph":38.2,
                 "engineTempC":96.4,"fuelPct":61.0,"payloadTonnes":221.5}""".formatted(at));

        client.get().uri("/v1/trucks/HT-101").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("""
                        {"truckId":"HT-101","model":"Cat 793F","status":"HAULING","position":{"x":1234.5,"y":567.8},\
                        "headingDeg":0.0,"speedKph":38.2,"engineTempC":96.4,"fuelPct":61.0,"payloadTonnes":221.5,\
                        "updatedAt":"%s"}""".formatted(at));
    }

    @Test
    void listsTheTrucksThatHaveReportedInTruckOrder() {
        var at = clock.advance(Duration.ofSeconds(1));
        post(reading("HT-103", at, 30, 90, 60, 0));
        post(reading("HT-101", at, 30, 90, 60, 0));

        client.get().uri("/v1/trucks").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[*].truckId").isEqualTo(List.of("HT-101", "HT-103"));
    }

    @Test
    void ignoresAReadingOlderThanWhatItAlreadyHas() {
        var at = clock.advance(Duration.ofSeconds(10));
        post(reading("HT-102", at, 30, 90, 60, 0));
        post(reading("HT-102", at.minusSeconds(5), 45, 90, 60, 0));

        client.get().uri("/v1/trucks/HT-102").exchange()
                .expectBody().jsonPath("$.speedKph").isEqualTo(30.0);
    }

    @Test
    void keepsReadingsNewestFirst() {
        var first = clock.advance(Duration.ofSeconds(1));
        post(reading("HT-101", first, 30, 90, 60, 0));
        post(reading("HT-101", first.plusSeconds(1), 31, 90, 60, 0));
        post(reading("HT-101", first.plusSeconds(2), 32, 90, 60, 0));

        client.get().uri("/v1/trucks/HT-101/readings?limit=2").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[*].speedKph").isEqualTo(List.of(32.0, 31.0));
        client.get().uri("/v1/trucks/HT-101/readings").exchange()
                .expectBody().jsonPath("$.length()").isEqualTo(3);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            /v1/trucks/HT-999          | HT-999 is not in the fleet
            /v1/trucks/HT-106          | HT-106 has not reported yet
            /v1/trucks/HT-999/readings | HT-999 is not in the fleet
            """)
    void answersAnUnknownOrSilentTruckWith404(String path, String detail) {
        client.get().uri(path).exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class).isEqualTo(problem("Not Found", 404, detail));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            /v1/trucks/HT-101/readings?limit=0   | limit must be from 1 to 300
            /v1/trucks/HT-101/readings?limit=301 | limit must be from 1 to 300
            /v1/alerts?limit=0                   | limit must be from 1 to 200
            /v1/alerts?limit=201                 | limit must be from 1 to 200
            /v1/alerts?limit=many                | Failed to convert 'limit' with value: 'many'
            """)
    void rejectsALimitOutOfRange(String path, String detail) {
        client.get().uri(path).exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo(problem("Bad Request", 400, detail));
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            {}                                                                     | truckId is required; recordedAt is required; position needs x and y; speedKph is required; engineTempC is required; fuelPct is required; payloadTonnes is required
            {"truckId":"HT-999","recordedAt":"2027-01-01T00:00:00Z","position":{"x":1}} | HT-999 is not in the fleet; recordedAt is in the future; position needs x and y; speedKph is required; engineTempC is required; fuelPct is required; payloadTonnes is required
            {"truckId":"HT-101","recordedAt":"2026-10-20T09:00:00Z","position":{"y":1}} | position needs x and y; speedKph is required; engineTempC is required; fuelPct is required; payloadTonnes is required
            {"truckId":"HT-101","recordedAt":"2026-10-20T09:00:00Z","position":{"x":-1,"y":1},"speedKph":-1,"engineTempC":201,"fuelPct":101,"payloadTonnes":401} | position is outside the site; speedKph must be from 0 to 150; engineTempC must be from -40 to 200; fuelPct must be from 0 to 100; payloadTonnes must be from 0 to 400
            {"truckId":"HT-101","recordedAt":"2026-10-20T09:00:00Z","position":{"x":1,"y":1},"speedKph":151,"engineTempC":-41,"fuelPct":-1,"payloadTonnes":-1} | speedKph must be from 0 to 150; engineTempC must be from -40 to 200; fuelPct must be from 0 to 100; payloadTonnes must be from 0 to 400
            """)
    void rejectsAnInvalidReadingWithEveryProblemAtOnce(String body, String detail) {
        client.post().uri("/v1/telemetry").contentType(MediaType.APPLICATION_JSON).body(body).exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo(problem("Bad Request", 400, detail));
    }

    @Test
    void rejectsABodyItCannotRead() {
        client.post().uri("/v1/telemetry").contentType(MediaType.APPLICATION_JSON).body("{").exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo(problem("Bad Request", 400, "Failed to read request"));
        client.post().uri("/v1/telemetry").contentType(MediaType.TEXT_PLAIN).body("HT-101").exchange()
                .expectStatus().isEqualTo(415)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void storesAlertsAndListsThemNewestFirst() {
        var hot = clock.advance(Duration.ofSeconds(1));
        for (var second = 0; second < 3; second++) {
            post(reading("HT-104", hot.plusSeconds(second), 30, 111, 60, 0));
        }
        var thirsty = clock.advance(Duration.ofSeconds(5));
        for (var second = 0; second < 3; second++) {
            post(reading("HT-105", thirsty.plusSeconds(second), 30, 90, 10, 0));
        }

        client.get().uri("/v1/alerts").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[*].truckId").isEqualTo(List.of("HT-105", "HT-104"))
                .jsonPath("$[0].type").isEqualTo("LOW_FUEL")
                .jsonPath("$[0].severity").isEqualTo("WARNING")
                .jsonPath("$[0].message").isEqualTo("Fuel at 10.0% is below 15%")
                .jsonPath("$[0].raisedAt").isEqualTo(thirsty.toString())
                .jsonPath("$[1].type").isEqualTo("OVERHEATING")
                .jsonPath("$[1].severity").isEqualTo("CRITICAL")
                .jsonPath("$[1].id").value(Long.class, id -> assertThat(id).isPositive());
        client.get().uri("/v1/alerts?truckId=HT-104").exchange()
                .expectBody().jsonPath("$[*].truckId").isEqualTo(List.of("HT-104"));
        client.get().uri("/v1/alerts?limit=1").exchange()
                .expectBody().jsonPath("$[*].truckId").isEqualTo(List.of("HT-105"));
    }

    @Test
    void answersEverythingElseWithProblemsToo() {
        client.method(HttpMethod.DELETE).uri("/v1/trucks").exchange()
                .expectStatus().isEqualTo(405)
                .expectBody(String.class).isEqualTo(problem("Method Not Allowed", 405, "Method 'DELETE' is not supported."));
        client.get().uri("/v2/trucks").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void letsTheDashboardCallFromItsOwnOrigin() {
        client.options().uri("/v1/trucks")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        client.get().uri("/v1/trucks").header("Origin", "https://other-dashboard.example").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://other-dashboard.example");
        client.get().uri("/v1/trucks").header("Origin", "https://elsewhere.example").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void reportsHealth() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void runsTheSimulatorOnItsSchedulerAndCanPauseIt() {
        assertThat(simulator.isRunning()).isTrue();
        for (var tick = 0; tick < 3; tick++) {
            clock.advance(Duration.ofSeconds(1));
            scheduler.step();
        }

        client.get().uri("/v1/trucks").exchange()
                .expectBody().jsonPath("$[*].truckId").isEqualTo(List.of(
                        "HT-101", "HT-102", "HT-103", "HT-104", "HT-105", "HT-106", "HT-107", "HT-108"));
        client.get().uri("/v1/trucks/HT-108/readings").exchange()
                .expectBody().jsonPath("$.length()").isEqualTo(3);

        simulator.stop();
        simulator.stop();
        assertThat(simulator.isRunning()).isFalse();
        assertThat(scheduler.scheduledTasks()).isZero();
        simulator.start();
        assertThat(scheduler.scheduledTasks()).isOne();
    }

    @Test
    void takesReadingsOnlyForTrucksInTheFleet() {
        var stranger = new Reading("HT-999", clock.instant(), new Position(1, 1), 0, 80, 50, 0);

        assertThatIllegalArgumentException().isThrownBy(() -> telemetry.ingest(stranger))
                .withMessage("HT-999 is not in the fleet");
    }

    private static String problem(String title, int status, String detail) {
        return "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}".formatted(title, status, detail);
    }
}
