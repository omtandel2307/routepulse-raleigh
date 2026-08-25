package com.routepulse.ingestion;

import java.time.Instant;
import java.util.List;

public record ServiceAlertSnapshotEvent(
    String agencyId,
    Instant recordedAt,
    List<ServiceAlertEvent> alerts) {
}
