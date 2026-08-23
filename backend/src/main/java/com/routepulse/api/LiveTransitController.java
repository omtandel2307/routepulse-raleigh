package com.routepulse.api;

import com.routepulse.live.LiveTransitRepository;
import com.routepulse.live.LiveTransitRepository.RouteStatus;
import com.routepulse.live.LiveTransitRepository.VehicleView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1")
public class LiveTransitController {

  private final LiveTransitRepository liveTransit;

  public LiveTransitController(LiveTransitRepository liveTransit) {
    this.liveTransit = liveTransit;
  }

  @GetMapping("/vehicles")
  public List<VehicleView> vehicles(
      @RequestParam(defaultValue = "wolfline") String agencyId,
      @RequestParam(defaultValue = "5") @Min(1) @Max(120) int activeWithinMinutes) {
    return liveTransit.activeVehicles(agencyId, cutoff(activeWithinMinutes));
  }

  @GetMapping("/routes/{routeId}/vehicles")
  public List<VehicleView> routeVehicles(
      @PathVariable String routeId,
      @RequestParam(defaultValue = "wolfline") String agencyId,
      @RequestParam(defaultValue = "5") @Min(1) @Max(120) int activeWithinMinutes) {
    return liveTransit.activeVehiclesForRoute(agencyId, routeId, cutoff(activeWithinMinutes));
  }

  @GetMapping("/routes/{routeId}/status")
  public ResponseEntity<RouteStatus> routeStatus(
      @PathVariable String routeId,
      @RequestParam(defaultValue = "wolfline") String agencyId,
      @RequestParam(defaultValue = "5") @Min(1) @Max(120) int activeWithinMinutes) {
    return ResponseEntity.of(liveTransit.routeStatus(agencyId, routeId, cutoff(activeWithinMinutes)));
  }

  private static Instant cutoff(int activeWithinMinutes) {
    return Instant.now().minus(activeWithinMinutes, ChronoUnit.MINUTES);
  }
}
