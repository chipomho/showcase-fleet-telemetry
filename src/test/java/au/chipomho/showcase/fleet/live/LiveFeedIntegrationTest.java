package au.chipomho.showcase.fleet.live;

import au.chipomho.showcase.fleet.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Connects with the JDK's own WebSocket client, so the handshake, the origin check and the frames
 * are all real.
 */
class LiveFeedIntegrationTest extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void sendsASnapshotFirstThenEachUpdateAndAlert() throws Exception {
        var start = clock.advance(Duration.ofSeconds(1));
        post(reading("HT-107", start, 30, 90, 60, 0));

        try (var feed = Feed.open(port, ALLOWED_ORIGIN)) {
            var snapshot = feed.next();
            assertThat(snapshot.get("type").asString()).isEqualTo("SNAPSHOT");
            assertThat(snapshot.get("trucks").get(0).get("truckId").asString()).isEqualTo("HT-107");

            post(reading("HT-107", start.plusSeconds(1), 32, 90, 60, 0));
            var update = feed.next();
            assertThat(update.get("type").asString()).isEqualTo("TRUCK_UPDATED");
            assertThat(update.get("truck").get("speedKph").asDouble()).isEqualTo(32.0);

            for (var second = 2; second < 5; second++) {
                post(reading("HT-108", start.plusSeconds(second), 30, 108, 60, 0));
            }
            var types = new ArrayList<String>();
            for (var frame = 0; frame < 4; frame++) {
                types.add(feed.next().get("type").asString());
            }
            assertThat(types).containsExactly("TRUCK_UPDATED", "TRUCK_UPDATED", "TRUCK_UPDATED", "ALERT_RAISED");
        }
    }

    @Test
    void sendsTheAlertItself() throws Exception {
        var start = clock.advance(Duration.ofSeconds(1));
        try (var feed = Feed.open(port, ALLOWED_ORIGIN)) {
            feed.next();
            for (var second = 0; second < 3; second++) {
                post(reading("HT-106", start.plusSeconds(second), 30, 90, 3, 0));
                feed.next();
            }
            var alert = feed.next();

            assertThat(alert.get("type").asString()).isEqualTo("ALERT_RAISED");
            assertThat(alert.get("alert").get("truckId").asString()).isEqualTo("HT-106");
            assertThat(alert.get("alert").get("type").asString()).isEqualTo("LOW_FUEL");
            assertThat(alert.get("alert").get("severity").asString()).isEqualTo("CRITICAL");
            assertThat(alert.get("alert").get("message").asString()).isEqualTo("Fuel at 3.0% is below 15%");
            assertThat(alert.get("alert").get("id").asLong()).isPositive();
        }
    }

    @Test
    void carriesTheSimulatorsReadings() throws Exception {
        try (var feed = Feed.open(port, ALLOWED_ORIGIN)) {
            feed.next();
            clock.advance(Duration.ofSeconds(1));
            scheduler.step();

            var trucks = new ArrayList<String>();
            for (var frame = 0; frame < 8; frame++) {
                trucks.add(feed.next().get("truck").get("truckId").asString());
            }
            assertThat(trucks).containsExactly("HT-101", "HT-102", "HT-103", "HT-104", "HT-105", "HT-106", "HT-107", "HT-108");
        }
    }

    @Test
    void refusesABrowserFromAnotherOrigin() {
        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> Feed.open(port, "https://elsewhere.example"))
                .havingCause()
                .isInstanceOfSatisfying(WebSocketHandshakeException.class,
                        refused -> assertThat(refused.getResponse().statusCode()).isEqualTo(403));
    }

    /**
     * A live feed subscription that queues each text frame as it arrives.
     */
    private static final class Feed implements WebSocket.Listener, AutoCloseable {

        private final HttpClient http = HttpClient.newHttpClient();
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        private WebSocket socket;

        static Feed open(int port, String origin) throws Exception {
            var feed = new Feed();
            try {
                feed.socket = feed.http.newWebSocketBuilder()
                        .header("Origin", origin)
                        .buildAsync(URI.create("ws://localhost:" + port + "/v1/live"), feed)
                        .get(10, TimeUnit.SECONDS);
                return feed;
            } catch (Exception e) {
                feed.http.close();
                throw e;
            }
        }

        JsonNode next() throws InterruptedException {
            var frame = frames.poll(10, TimeUnit.SECONDS);
            assertThat(frame).as("a frame within 10 seconds").isNotNull();
            return JSON.readTree(frame);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                frames.add(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void close() {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            socket.abort();
            http.close();
        }
    }
}
