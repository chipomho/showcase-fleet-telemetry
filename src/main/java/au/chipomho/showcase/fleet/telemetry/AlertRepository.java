package au.chipomho.showcase.fleet.telemetry;

import au.chipomho.showcase.fleet.domain.Alert;
import au.chipomho.showcase.fleet.domain.AlertType;
import au.chipomho.showcase.fleet.domain.Breach;
import au.chipomho.showcase.fleet.domain.Severity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Alert history in Postgres.
 */
@Repository
public class AlertRepository {

    private static final String COLUMNS = "id, truck_id, type, severity, message, raised_at";

    private static final RowMapper<Alert> ALERT = (row, _) -> new Alert(
            row.getLong("id"),
            row.getString("truck_id"),
            AlertType.valueOf(row.getString("type")),
            Severity.valueOf(row.getString("severity")),
            row.getString("message"),
            row.getObject("raised_at", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    AlertRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Alert save(String truckId, Breach breach, Instant raisedAt) {
        var id = jdbc.sql("""
                        insert into alert (truck_id, type, severity, message, raised_at)
                        values (:truckId, :type, :severity, :message, :raisedAt)
                        returning id""")
                .param("truckId", truckId)
                .param("type", breach.type().name())
                .param("severity", breach.severity().name())
                .param("message", breach.message())
                .param("raisedAt", OffsetDateTime.ofInstant(raisedAt, ZoneOffset.UTC))
                .query(Long.class)
                .single();
        return new Alert(id, truckId, breach.type(), breach.severity(), breach.message(), raisedAt);
    }

    /**
     * Newest first, optionally for one truck.
     */
    public List<Alert> latest(int limit, Optional<String> truckId) {
        return truckId
                .map(id -> jdbc.sql("select " + COLUMNS + " from alert where truck_id = :truckId"
                                + " order by raised_at desc, id desc limit :limit")
                        .param("truckId", id))
                .orElseGet(() -> jdbc.sql("select " + COLUMNS + " from alert order by raised_at desc, id desc limit :limit"))
                .param("limit", limit)
                .query(ALERT)
                .list();
    }
}
