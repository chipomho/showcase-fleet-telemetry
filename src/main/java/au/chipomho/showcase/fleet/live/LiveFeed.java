package au.chipomho.showcase.fleet.live;

import au.chipomho.showcase.fleet.domain.TruckState;
import au.chipomho.showcase.fleet.telemetry.FleetEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * The {@code /v1/live} WebSocket. A new subscriber gets a snapshot of every truck first, then each
 * update and alert as it happens. Sending the snapshot and joining the broadcast happen under the
 * same lock as a broadcast, so nothing published after the snapshot was read can be missed.
 * Messages from clients are ignored; the feed only talks.
 */
public class LiveFeed extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(LiveFeed.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Supplier<List<TruckState>> snapshot;
    private final ObjectWriter writer;
    private final Map<String, WebSocketSession> subscribers = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public LiveFeed(Supplier<List<TruckState>> snapshot, JsonMapper json) {
        this.snapshot = snapshot;
        this.writer = json.writerFor(FleetEvent.class);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var subscriber = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        lock.lock();
        try {
            if (send(subscriber, frame(new FleetEvent.Snapshot(snapshot.get())))) {
                subscribers.put(session.getId(), subscriber);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscribers.remove(session.getId());
    }

    @EventListener
    public void publish(FleetEvent event) {
        var frame = frame(event);
        lock.lock();
        try {
            subscribers.values().removeIf(subscriber -> !send(subscriber, frame));
        } finally {
            lock.unlock();
        }
    }

    private TextMessage frame(FleetEvent event) {
        return new TextMessage(writer.writeValueAsString(event));
    }

    /**
     * A subscriber that can't be sent to, because it has gone or fallen too far behind, is closed
     * and dropped rather than allowed to hold up everyone else.
     */
    private static boolean send(WebSocketSession subscriber, TextMessage frame) {
        try {
            subscriber.sendMessage(frame);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.debug("Dropping live feed subscriber {}", subscriber.getId(), e);
            closeQuietly(subscriber);
            return false;
        }
    }

    private static void closeQuietly(WebSocketSession subscriber) {
        try {
            subscriber.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException | RuntimeException e) {
            LOG.debug("Could not close live feed subscriber {}", subscriber.getId(), e);
        }
    }
}
