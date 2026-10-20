package au.chipomho.showcase.fleet.telemetry;

import au.chipomho.showcase.fleet.domain.Fleet;
import au.chipomho.showcase.fleet.domain.Reading;
import au.chipomho.showcase.fleet.domain.Truck;
import au.chipomho.showcase.fleet.domain.TruckState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Each truck's latest state and its last {@value #KEPT_READINGS} readings, newest first, in Redis.
 */
@Component
public class TruckStore {

    static final int KEPT_READINGS = 300;

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Fleet fleet;

    TruckStore(StringRedisTemplate redis, JsonMapper json, Fleet fleet) {
        this.redis = redis;
        this.json = json;
        this.fleet = fleet;
    }

    public Optional<TruckState> find(String truckId) {
        return Optional.ofNullable(redis.opsForValue().get(stateKey(truckId)))
                .map(text -> json.readValue(text, TruckState.class));
    }

    /**
     * Every truck that has reported, in truck id order.
     */
    public List<TruckState> all() {
        var keys = fleet.trucks().stream().map(Truck::id).map(TruckStore::stateKey).toList();
        return Objects.requireNonNull(redis.opsForValue().multiGet(keys)).stream()
                .filter(Objects::nonNull)
                .map(text -> json.readValue(text, TruckState.class))
                .toList();
    }

    public void save(TruckState state, Reading reading) {
        redis.opsForValue().set(stateKey(state.truckId()), json.writeValueAsString(state));
        var readings = readingsKey(state.truckId());
        redis.opsForList().leftPush(readings, json.writeValueAsString(reading));
        redis.opsForList().trim(readings, 0, KEPT_READINGS - 1);
    }

    public List<Reading> readings(String truckId, int limit) {
        return Objects.requireNonNull(redis.opsForList().range(readingsKey(truckId), 0, limit - 1)).stream()
                .map(text -> json.readValue(text, Reading.class))
                .toList();
    }

    private static String stateKey(String truckId) {
        return "truck:" + truckId;
    }

    private static String readingsKey(String truckId) {
        return "truck:" + truckId + ":readings";
    }
}
