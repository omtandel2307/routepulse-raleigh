package com.routepulse.ingestion;

import java.time.Instant;
import java.util.List;

public record ServiceAlertEvent(
    String alertId,
    String cause,
    String effect,
    String header,
    String description,
    String url,
    List<String> routeIds,
    List<String> stopIds,
    List<ActivePeriod> activePeriods) {

  public record ActivePeriod(Instant startsAt, Instant endsAt) {
  }
}
