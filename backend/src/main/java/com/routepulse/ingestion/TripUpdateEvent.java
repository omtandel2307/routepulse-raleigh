package com.routepulse.ingestion;

import java.time.Instant;

public record TripUpdateEvent(
    String agencyId,
    String tripId,
    String routeId,
    String vehicleId,
    Integer delaySeconds,
    String nextStopId,
    Integer nextStopSequence,
    Instant estimatedArrival,
    Instant estimatedDeparture,
    Instant recordedAt
) {
}
