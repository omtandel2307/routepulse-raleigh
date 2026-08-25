package com.routepulse.ingestion;

import com.routepulse.config.KafkaConfig;
import com.routepulse.config.RoutePulseProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "routepulse.ingestion.enabled", havingValue = "true")
public class VehiclePositionPoller {
  private static final Logger log = LoggerFactory.getLogger(VehiclePositionPoller.class);
  private final RoutePulseProperties props;
  private final GtfsRealtimeClient client;
  private final KafkaTemplate<String, Object> kafka;

  public VehiclePositionPoller(RoutePulseProperties p, GtfsRealtimeClient c, KafkaTemplate<String, Object> k) {
    props = p;
    client = c;
    kafka = k;
  }

  @Scheduled(fixedDelayString = "${routepulse.ingestion.fixed-delay-ms}")
  public void poll() {
    props.agencies().stream()
        .filter(RoutePulseProperties.Agency::enabled)
        .forEach(this::pollAgency);
  }

  private void pollAgency(RoutePulseProperties.Agency agency) {
    try {
      var feed = client.fetch(agency.feeds().vehiclePositions());
      long timestamp = feed.getHeader().hasTimestamp() ? feed.getHeader().getTimestamp() : 0;
      feed.getEntityList().forEach(entity ->
          GtfsRealtimeEventMapper.vehiclePosition(agency.id(), entity, timestamp)
              .ifPresent(event -> kafka.send(KafkaConfig.VEHICLE_POSITIONS_TOPIC,
                  event.agencyId() + ":" + event.vehicleId(), event)));
    } catch (RuntimeException exception) {
      log.warn("Unable to poll vehicle positions for agency {}", agency.id(), exception);
    }

    try {
      var feed = client.fetch(agency.feeds().tripUpdates());
      long timestamp = feed.getHeader().hasTimestamp() ? feed.getHeader().getTimestamp() : 0;
      feed.getEntityList().forEach(entity ->
          GtfsRealtimeEventMapper.tripUpdate(agency.id(), entity, timestamp)
              .ifPresent(event -> kafka.send(KafkaConfig.TRIP_UPDATES_TOPIC,
                  event.agencyId() + ":" + event.tripId(), event)));
    } catch (RuntimeException exception) {
      log.warn("Unable to poll trip updates for agency {}", agency.id(), exception);
    }

    try {
      var feed = client.fetch(agency.feeds().alerts());
      Instant recordedAt = feed.getHeader().hasTimestamp()
          ? Instant.ofEpochSecond(feed.getHeader().getTimestamp()) : Instant.now();
      var alerts = feed.getEntityList().stream()
          .map(GtfsRealtimeEventMapper::serviceAlert)
          .flatMap(java.util.Optional::stream)
          .toList();
      var snapshot = new ServiceAlertSnapshotEvent(agency.id(), recordedAt, alerts);
      kafka.send(KafkaConfig.SERVICE_ALERTS_TOPIC, agency.id(), snapshot);
    } catch (RuntimeException exception) {
      log.warn("Unable to poll service alerts for agency {}", agency.id(), exception);
    }
  }
}
