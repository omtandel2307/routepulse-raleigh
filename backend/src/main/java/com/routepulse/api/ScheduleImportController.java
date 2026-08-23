package com.routepulse.api;

import com.routepulse.schedule.ScheduleImportService;
import com.routepulse.schedule.ScheduleImportService.ImportSummary;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/agencies")
public class ScheduleImportController {

  private final ScheduleImportService importer;

  public ScheduleImportController(ScheduleImportService importer) {
    this.importer = importer;
  }

  @PostMapping("/{agencyId}/schedule/import")
  public ImportSummary importSchedule(@PathVariable String agencyId) {
    return importer.importAgency(agencyId);
  }
}
