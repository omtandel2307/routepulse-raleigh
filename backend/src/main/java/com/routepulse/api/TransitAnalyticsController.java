package com.routepulse.api;

import com.routepulse.analytics.TransitAnalyticsRepository;
import com.routepulse.analytics.TransitAnalyticsRepository.AnalyticsSummary;
import com.routepulse.analytics.TransitAnalyticsRepository.RoutePerformance;
import com.routepulse.analytics.TransitAnalyticsRepository.TimelinePoint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/analytics")
public class TransitAnalyticsController {

  private final TransitAnalyticsRepository analytics;

  public TransitAnalyticsController(TransitAnalyticsRepository analytics) {
    this.analytics = analytics;
  }

  @GetMapping("/summary")
  public AnalyticsSummary summary(
      @RequestParam(defaultValue = "wolfline") String agencyId,
      @RequestParam(required = false) String routeId,
      @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours) {
    Instant now = Instant.now();
    return analytics.summary(agencyId, routeId, now.minus(hours, ChronoUnit.HOURS), now);
  }

  @GetMapping("/timeline")
  public List<TimelinePoint> timeline(
      @RequestParam(defaultValue = "wolfline") String agencyId,
      @RequestParam(required = false) String routeId,
      @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours,
      @RequestParam(defaultValue = "60") @Min(5) @Max(1440) int bucketMinutes) {
    return analytics.timeline(
        agencyId, routeId, Instant.now().minus(hours, ChronoUnit.HOURS), bucketMinutes);
  }

  @GetMapping("/routes")
  public List<RoutePerformance> routes(
      @RequestParam(defaultValue = "wolfline") String agencyId,
      @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours) {
    return analytics.routePerformance(agencyId, Instant.now().minus(hours, ChronoUnit.HOURS));
  }
}
