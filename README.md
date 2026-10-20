# Fleet Telemetry

Live monitoring for a fleet of haul trucks on an open-pit mine, the kind of picture a mine
control room watches. A built-in simulator drives six Cat 793F trucks round a haul loop: load at
the shovel, haul to the crusher, dump, return empty. Each truck's readings come in over HTTP, its
latest state is kept in Redis, alert rules run over a rolling window of its readings, alert
history goes to Postgres, and a WebSocket pushes every update and alert to the dashboard.

## Alerts

Each rule judges the mean of the last 30 seconds of a truck's readings, once there are at least
three, so a single odd reading can't raise anything.

| Alert         | Warning       | Critical      | Clears        |
|---------------|---------------|---------------|---------------|
| `OVERHEATING` | above 105°C   | above 110°C   | below 100°C   |
| `LOW_FUEL`    | below 15%     | below 5%      | above 20%     |
| `OVERSPEED`   | above 55 km/h | above 60 km/h | below 52 km/h |
| `OVERLOAD`    | above 250 t   | above 272 t   | below 240 t   |

An alert is raised when its condition starts and not again until it has cleared, at the safer
limit in the last column. The one exception is escalation: a warning that worsens past the
critical limit raises one critical alert. The simulator gets some cycles wrong on purpose (a
cooling fault, a heavy foot on the way back, an overloaded tray, fuel left too long) so alerts
turn up within a few minutes.

## Running it

You need Docker or Podman with Compose. Add
`127.0.0.1 fleet-telemetry-api.showcases.chipomho.local` to your hosts file and run
`docker compose up --build -d`. The first start creates a local CA in `.certs/` and a wildcard
certificate for `*.showcases.chipomho.local` that Traefik serves on 443; trust `.certs/ca.crt` or
pass it to curl with `--cacert`. `SHOWCASES_CA_DIR` (an existing directory) shares one CA between
the showcase projects, and `SHOWCASES_HTTP_PORT`/`SHOWCASES_HTTPS_PORT` move Traefik off 80 and
443. `docker compose down -v` stops everything and drops the alert history.

The simulator runs one tick a second at four times real speed, so a haul cycle takes about four
minutes. `FLEET_SIMULATOR_TRUCKS` sets the fleet size and `FLEET_SIMULATOR_ENABLED=false` turns it
off; `ALLOWED_ORIGINS` lists the browser origins for CORS and the WebSocket.

## The API

| Endpoint                                  | Returns                                            |
|-------------------------------------------|----------------------------------------------------|
| `GET /v1/site`                            | The pit, the haul loop and the load and dump zones |
| `GET /v1/trucks`, `GET /v1/trucks/{id}`   | Latest state per truck                             |
| `GET /v1/trucks/{id}/readings?limit=60`   | Recent readings, newest first (1 to 300)           |
| `GET /v1/alerts?limit=50&truckId=HT-101`  | Alert history, newest first (1 to 200)             |
| `POST /v1/telemetry`                      | 202, after a reading is accepted                   |
| `wss://…/v1/live`                         | `SNAPSHOT`, then `TRUCK_UPDATED` and `ALERT_RAISED` |

Errors are RFC 9457 problems. Coordinates are metres from the site's south-west corner.

```
$ curl --cacert .certs/ca.crt https://fleet-telemetry-api.showcases.chipomho.local/v1/trucks/HT-101
{"truckId":"HT-101","model":"Cat 793F","status":"HAULING","position":{"x":1052.2,"y":660.9},"headingDeg":68.1,"speedKph":39.4,"engineTempC":88.9,"fuelPct":75.3,"payloadTonnes":233.3,"updatedAt":"2026-10-20T16:07:46.489Z"}
```

A frame from the live feed, when a truck's cooling fault got worse:

```
{"type":"ALERT_RAISED","alert":{"id":3,"truckId":"HT-105","type":"OVERHEATING","severity":"CRITICAL","message":"Engine temperature 110.3°C is above 105°C","raisedAt":"2026-10-20T16:08:53.246Z"}}
```

(The curl that ships with Windows also needs `--ssl-no-revoke`, because a local CA has no
revocation list.)

## Design notes

The simulator is plain Java, seeded and given the time on each tick, and it only produces
readings, the way a truck's telemetry unit would. They go through the same service as readings
posted over HTTP, which works out each truck's status (from the zones, its speed and payload) and
heading (from its last move) rather than trusting the sender.

The alert rules are a sealed interface with one record per rule holding its limits; the monitor
keeps a time-based window per truck and the severity each active alert was raised at, which is
all hysteresis and escalation need. The live feed sends a new subscriber its snapshot and adds it
to the broadcast under the lock a broadcast takes, so no update falls between the two, and drops a
subscriber that can't keep up rather than letting it hold up the rest. Underneath it is Spring
Boot on virtual threads, `JdbcClient` and Spring Data Redis.

## Tests

`./gradlew check` fails the build unless line and branch coverage are both 100%. The integration
tests need Docker or Podman: they run against Postgres 18 and Redis 8 in Testcontainers, step the
simulator by hand with their own clock and scheduler, and use the JDK's WebSocket client for the
live feed. The domain tests cover the rules at their limits, hysteresis and escalation, and a
seeded simulator that is repeatable and gives every rule something to find.
