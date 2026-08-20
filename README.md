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

Live ingestion is disabled by default. Enable it with:

```powershell
$env:ROUTEPULSE_INGESTION_ENABLED = 'true'
docker compose up -d --build api
```

Wolfline feeds:

- Schedule: `https://passio3.com/ncstateuni/passioTransit/gtfs/google_transit.zip`
- Vehicle positions: `https://passio3.com/ncstateuni/passioTransit/gtfs/realtime/vehiclePositions`
- Trip updates: `https://passio3.com/ncstateuni/passioTransit/gtfs/realtime/tripUpdates`
- Alerts: `https://passio3.com/ncstateuni/passioTransit/gtfs/realtime/serviceAlerts`

Redis is exposed on host port `6380` to avoid collisions; containers use `6379`.

