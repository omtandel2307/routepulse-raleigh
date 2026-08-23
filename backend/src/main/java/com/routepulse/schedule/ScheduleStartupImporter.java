package com.routepulse.schedule;

import com.routepulse.config.RoutePulseProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "routepulse.schedule.import-on-startup", havingValue = "true")
public class ScheduleStartupImporter implements ApplicationRunner {

  private final RoutePulseProperties properties;
  private final ScheduleImportService importer;

  public ScheduleStartupImporter(RoutePulseProperties properties, ScheduleImportService importer) {
    this.properties = properties;
    this.importer = importer;
  }

  @Override
  public void run(ApplicationArguments args) {
    properties.agencies().stream()
        .filter(RoutePulseProperties.Agency::enabled)
        .forEach(agency -> importer.importAgency(agency.id()));
  }
}
