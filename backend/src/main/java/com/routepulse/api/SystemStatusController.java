package com.routepulse.api;

import com.routepulse.config.RoutePulseProperties;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {
  private final RoutePulseProperties properties;

  public SystemStatusController(RoutePulseProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/status")
  public SystemStatus status() {
    return new SystemStatus("RoutePulse Raleigh", properties.ingestion().enabled(),
        properties.agencies().stream().map(a -> new AgencyStatus(a.id(), a.name(), a.enabled())).toList(),
        Instant.now());
  }

  public record SystemStatus(String application, boolean ingestionEnabled, List<AgencyStatus> agencies,
      Instant timestamp) {
  }

  public record AgencyStatus(String id, String name, boolean enabled) {
  }
}
