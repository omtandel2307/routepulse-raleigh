package com.routepulse.api;

import com.routepulse.catalog.TransitCatalogRepository;
import com.routepulse.catalog.TransitCatalogRepository.ImportStatus;
import com.routepulse.catalog.TransitCatalogRepository.RouteView;
import com.routepulse.catalog.TransitCatalogRepository.StopView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TransitCatalogController {

  private final TransitCatalogRepository catalog;

  public TransitCatalogController(TransitCatalogRepository catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/routes")
  public List<RouteView> routes(@RequestParam(defaultValue = "wolfline") String agencyId) {
    return catalog.routes(agencyId);
  }

  @GetMapping("/stops")
  public List<StopView> stops(@RequestParam(defaultValue = "wolfline") String agencyId) {
    return catalog.stops(agencyId);
  }

  @GetMapping("/schedule/status")
  public ResponseEntity<ImportStatus> scheduleStatus(
      @RequestParam(defaultValue = "wolfline") String agencyId) {
    return ResponseEntity.of(catalog.importStatus(agencyId));
  }
}
