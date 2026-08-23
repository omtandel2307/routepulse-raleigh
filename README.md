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
routes, stops, trips, stop times, calendars, and service exceptions:

```powershell
Invoke-RestMethod -Method Post `
  http://localhost:8080/api/v1/admin/agencies/wolfline/schedule/import
```

Inspect the imported catalog:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/schedule/status
Invoke-RestMethod http://localhost:8080/api/v1/routes
Invoke-RestMethod http://localhost:8080/api/v1/stops
```

To import automatically when the API starts, set
`ROUTEPULSE_SCHEDULE_IMPORT_ON_STARTUP=true`. Manual import is the safer development default.

Live ingestion is disabled by default. Enable it with:

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

Next: surface live vehicles and route health on the Angular dashboard, then build historical
reliability aggregates.
