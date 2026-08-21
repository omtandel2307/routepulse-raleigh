package com.routepulse.ingestion;

import com.routepulse.config.KafkaConfig;
import com.routepulse.config.RoutePulseProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "routepulse.ingestion.enabled", havingValue = "true")
public class VehiclePositionPoller {
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
    props.agencies().stream().filter(RoutePulseProperties.Agency::enabled)
        .forEach(a -> client.fetch(a.feeds().vehiclePositions()).getEntityList().stream()
            .filter(e -> e.hasVehicle() && e.getVehicle().hasPosition()).forEach(e -> {
              var v = e.getVehicle();
              var id = v.getVehicle().getId();
              var p = v.getPosition();
              kafka.send(KafkaConfig.VEHICLE_POSITIONS_TOPIC, a.id() + ":" + id,
                  new VehiclePositionEvent(a.id(), id, v.getTrip().getTripId(), v.getTrip().getRouteId(),
                      p.getLatitude(), p.getLongitude(), p.getBearing(), p.getSpeed(), v.getTimestamp()));
            }));
  }
}
