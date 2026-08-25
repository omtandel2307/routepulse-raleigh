package com.routepulse.api;

import com.routepulse.live.ServiceAlertRepository;
import com.routepulse.live.ServiceAlertRepository.ServiceAlertView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class ServiceAlertController {

  private final ServiceAlertRepository alerts;

  public ServiceAlertController(ServiceAlertRepository alerts) {
    this.alerts = alerts;
  }

  @GetMapping
  public List<ServiceAlertView> alerts(
      @RequestParam(defaultValue = "wolfline") String agencyId,
      @RequestParam(required = false) String routeId) {
    return alerts.activeAlerts(agencyId, routeId, Instant.now());
  }
}
