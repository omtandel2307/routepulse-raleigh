# RoutePulse Raleigh

Transit reliability analytics for NC State Wolfline, designed to expand to GoRaleigh.

## Stack

- Spring Boot 4 / Java 21 API and GTFS-Realtime ingestion
- Angular 22 dashboard
- PostgreSQL + Flyway, Redis, Kafka
- Docker Compose for the full local environment

## Start

```powershell
docker compose up -d --build
```

Open http://localhost:4200. API status is at http://localhost:8080/api/v1/system/status and health at http://localhost:8080/actuator/health.

## Import the Wolfline schedule

The first schedule import downloads the public GTFS archive and transactionally replaces Wolfline's
routes, stops, trips, stop times, route shape points, calendars, and service exceptions:

```powershell
Invoke-RestMethod -Method Post `
  http://localhost:8080/api/v1/admin/agencies/wolfline/schedule/import
```

Inspect the imported catalog:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/schedule/status
Invoke-RestMethod http://localhost:8080/api/v1/routes
Invoke-RestMethod http://localhost:8080/api/v1/routes/geometry
Invoke-RestMethod http://localhost:8080/api/v1/stops
Invoke-RestMethod http://localhost:8080/api/v1/stops/STOP_ID/arrivals
```

The dashboard uses Leaflet with OpenStreetMap streets. GTFS `shapes.txt` coordinates are
served as GeoJSON by `/api/v1/routes/geometry`, so selecting or clicking a route highlights its real
published path. Live bus markers update every 15 seconds and open vehicle detail popups when clicked.
Serviced stops appear as white map dots; clicking one shows its routes, accessibility status, and any
approaching buses currently reported by GTFS-Realtime.

To import automatically when the API starts, set
`ROUTEPULSE_SCHEDULE_IMPORT_ON_STARTUP=true`. Manual import is the safer development default.

Live ingestion is enabled by default in Docker Compose. To explicitly start or refresh the API with it enabled:

```powershell
$env:ROUTEPULSE_INGESTION_ENABLED = 'true'
docker compose up -d --build api
```

After one polling cycle (about 15 seconds), inspect the live state:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/v1/vehicles?activeWithinMinutes=5'

$route = Invoke-RestMethod http://localhost:8080/api/v1/routes |
  Where-Object shortName -eq '42' |
  Select-Object -First 1

Invoke-RestMethod "http://localhost:8080/api/v1/routes/$($route.id)/vehicles?activeWithinMinutes=5"
Invoke-RestMethod "http://localhost:8080/api/v1/routes/$($route.id)/status?activeWithinMinutes=5"
```

The API keeps an append-only vehicle-position history and replay-safe current vehicle/trip state in
PostgreSQL. Delay status is classified as `EARLY` (more than 5 minutes early), `ON_TIME` (within 5
minutes), `LATE` (more than 5 minutes late), or `UNKNOWN` when the source feed has no matching delay.

## Reliability analytics

Wolfline publishes predicted stop timestamps rather than a direct delay value. RoutePulse joins each
active prediction to its scheduled GTFS stop time and retains one guarded observation per trip per
minute. Implausible deviations and predictions without a matching active vehicle are discarded.

```powershell
Invoke-RestMethod 'http://localhost:8080/api/v1/analytics/summary?hours=24'
Invoke-RestMethod 'http://localhost:8080/api/v1/analytics/timeline?hours=24&bucketMinutes=60'
Invoke-RestMethod 'http://localhost:8080/api/v1/analytics/routes?hours=24'
```

All three endpoints accept `agencyId`; summary and timeline also accept `routeId`. The dashboard
provides 1-hour, 6-hour, 24-hour, and 7-day views.

Wolfline feeds:

- Schedule: `https://passio3.com/ncstateuni/passioTransit/gtfs/google_transit.zip`
- Vehicle positions: `https://passio3.com/ncstateuni/passioTransit/gtfs/realtime/vehiclePositions`
- Trip updates: `https://passio3.com/ncstateuni/passioTransit/gtfs/realtime/tripUpdates`
- Alerts: `https://passio3.com/ncstateuni/passioTransit/gtfs/realtime/serviceAlerts`

Redis is exposed on host port `6380` to avoid collisions; containers use `6379`.

## Current milestone

- Static Wolfline GTFS schedule import and PostgreSQL persistence
- Agency-scoped route, stop, trip, calendar, and stop-time identifiers
- Route and stop catalog APIs
- Vehicle-position and trip-update parsing and Kafka publishing (opt-in)
- Kafka consumers for vehicle history and replay-safe current state
- Active vehicle, route vehicle, and route delay-status APIs
- Responsive live vehicle dashboard with route filtering
- Minute-level delay history with feed-quality guardrails
- Reliability summary, timeline, and route-comparison APIs and charts

Next: ingest service alerts and add stop-level arrival predictions.
