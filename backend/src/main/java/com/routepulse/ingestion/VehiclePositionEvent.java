package com.routepulse.ingestion;

import java.time.Instant;

public record VehiclePositionEvent(
    String agencyId,
    String vehicleId,
    String tripId,
    String routeId,
    double latitude,
    double longitude,
    Float bearing,
    Float speed,
    Integer currentStopSequence,
    String currentStatus,
    Instant recordedAt
) {
}
