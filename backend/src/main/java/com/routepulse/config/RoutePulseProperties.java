package com.routepulse.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.List;
@ConfigurationProperties(prefix="routepulse")
public record RoutePulseProperties(Ingestion ingestion, List<Agency> agencies) {
  public record Ingestion(boolean enabled, long fixedDelayMs, Duration requestTimeout) {}
  public record Agency(String id, String name, boolean enabled, String timezone, Feeds feeds) {}
  public record Feeds(String schedule, String vehiclePositions, String tripUpdates, String alerts) {}
}

