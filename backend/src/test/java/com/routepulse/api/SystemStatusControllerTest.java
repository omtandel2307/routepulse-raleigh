package com.routepulse.api;
import com.routepulse.config.RoutePulseProperties;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class SystemStatusControllerTest {
 @Test void exposesConfiguredAgency(){var feeds=new RoutePulseProperties.Feeds("s","v","t","a");var props=new RoutePulseProperties(new RoutePulseProperties.Ingestion(false,15000,Duration.ofSeconds(10)),List.of(new RoutePulseProperties.Agency("wolfline","NC State Wolfline",true,"America/New_York",feeds)));assertThat(new SystemStatusController(props).status().agencies()).extracting(SystemStatusController.AgencyStatus::id).containsExactly("wolfline");}
}

