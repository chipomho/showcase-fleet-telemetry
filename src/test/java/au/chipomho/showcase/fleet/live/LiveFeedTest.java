package au.chipomho.showcase.fleet.live;

import au.chipomho.showcase.fleet.domain.Alert;
import au.chipomho.showcase.fleet.domain.AlertType;
import au.chipomho.showcase.fleet.domain.Position;
import au.chipomho.showcase.fleet.domain.Severity;
import au.chipomho.showcase.fleet.domain.TruckState;
import au.chipomho.showcase.fleet.domain.TruckStatus;
import au.chipomho.showcase.fleet.telemetry.FleetEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LiveFeedTest {

    private static final Instant AT = Instant.parse("2026-10-20T10:15:30Z");
    private static final TruckState TRUCK = new TruckState("HT-101", "Cat 793F", TruckStatus.HAULING,
            new Position(1234.5, 567.8), 90.0, 38.2, 96.4, 61.0, 221.5, AT);
    private static final String TRUCK_JSON = """
            {"truckId":"HT-101","model":"Cat 793F","status":"HAULING","position":{"x":1234.5,"y":567.8},\
            "headingDeg":90.0,"speedKph":38.2,"engineTempC":96.4,"fuelPct":61.0,"payloadTonnes":221.5,\
            "updatedAt":"2026-10-20T10:15:30Z"}""";

    private final LiveFeed feed = new LiveFeed(() -> List.of(TRUCK), JsonMapper.builder().build());

    @Test
    void sendsEachSubscriberASnapshotFirstAndThenEveryEventInTheContractsShape() {
        var first = new StubSession("1");
        var second = new StubSession("2");
        feed.afterConnectionEstablished(first);
        feed.afterConnectionEstablished(second);

        feed.publish(new FleetEvent.TruckUpdated(TRUCK));
        feed.publish(new FleetEvent.AlertRaised(new Alert(123, "HT-101", AlertType.OVERHEATING, Severity.CRITICAL,
                "Engine temperature 112.4°C is above 105°C", AT)));

        assertThat(first.sent).containsExactly(
                "{\"type\":\"SNAPSHOT\",\"trucks\":[" + TRUCK_JSON + "]}",
                "{\"type\":\"TRUCK_UPDATED\",\"truck\":" + TRUCK_JSON + "}",
                "{\"type\":\"ALERT_RAISED\",\"alert\":{\"id\":123,\"truckId\":\"HT-101\",\"type\":\"OVERHEATING\","
                        + "\"severity\":\"CRITICAL\",\"message\":\"Engine temperature 112.4°C is above 105°C\","
                        + "\"raisedAt\":\"2026-10-20T10:15:30Z\"}}");
        assertThat(second.sent).isEqualTo(first.sent);
    }

    @Test
    void dropsASubscriberItCanNoLongerSendTo() {
        var healthy = new StubSession("1");
        var gone = new StubSession("2");
        feed.afterConnectionEstablished(healthy);
        feed.afterConnectionEstablished(gone);
        gone.failSending = true;

        feed.publish(new FleetEvent.TruckUpdated(TRUCK));
        feed.publish(new FleetEvent.TruckUpdated(TRUCK));

        assertThat(healthy.sent).hasSize(3);
        assertThat(gone.sent).hasSize(1);
        assertThat(gone.closedWith).isEqualTo(CloseStatus.SESSION_NOT_RELIABLE);
    }

    @Test
    void neverSubscribesAClientThatMissedTheSnapshotEvenIfItCannotBeClosed() {
        var broken = new StubSession("1");
        broken.failSending = true;
        broken.failClosing = true;

        feed.afterConnectionEstablished(broken);
        feed.publish(new FleetEvent.TruckUpdated(TRUCK));

        assertThat(broken.sent).isEmpty();
        assertThat(broken.sendAttempts).isOne();
    }

    @Test
    void forgetsASubscriberOnceItHasGone() {
        var leaving = new StubSession("1");
        feed.afterConnectionEstablished(leaving);

        feed.afterConnectionClosed(leaving, CloseStatus.NORMAL);
        feed.publish(new FleetEvent.TruckUpdated(TRUCK));

        assertThat(leaving.sent).hasSize(1);
    }

    /**
     * Just enough of a WebSocket session to see what the feed sends and to make sending fail.
     */
    private static final class StubSession implements WebSocketSession {

        private final String id;
        private final List<String> sent = new ArrayList<>();
        private int sendAttempts;
        private boolean failSending;
        private boolean failClosing;
        private CloseStatus closedWith;

        StubSession(String id) {
            this.id = id;
        }

        @Override
        public void sendMessage(WebSocketMessage<?> message) throws IOException {
            sendAttempts++;
            if (failSending) {
                throw new IOException("Broken pipe");
            }
            sent.add(((TextMessage) message).getPayload());
        }

        @Override
        public void close(CloseStatus status) throws IOException {
            if (failClosing) {
                throw new IOException("Already gone");
            }
            closedWith = status;
        }

        @Override
        public void close() throws IOException {
            close(CloseStatus.NORMAL);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isOpen() {
            return closedWith == null;
        }

        @Override
        public URI getUri() {
            return null;
        }

        @Override
        public HttpHeaders getHandshakeHeaders() {
            return new HttpHeaders();
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 0;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 0;
        }

        @Override
        public List<WebSocketExtension> getExtensions() {
            return List.of();
        }
    }
}
