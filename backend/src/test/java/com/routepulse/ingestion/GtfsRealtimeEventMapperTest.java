package com.routepulse.ingestion;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.EntitySelector;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TimeRange;
import com.google.transit.realtime.GtfsRealtime.TranslatedString;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GtfsRealtimeEventMapperTest {

  @Test
  void mapsVehiclePosition() {
    FeedEntity entity = FeedEntity.newBuilder()
        .setId("entity-1")
        .setVehicle(VehiclePosition.newBuilder()
            .setVehicle(VehicleDescriptor.newBuilder().setId("bus-42"))
            .setTrip(TripDescriptor.newBuilder().setTripId("trip-1").setRouteId("route-1"))
            .setPosition(Position.newBuilder().setLatitude(35.78f).setLongitude(-78.67f)
                .setBearing(90).setSpeed(8.5f))
            .setCurrentStopSequence(4)
            .setCurrentStatus(VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO)
            .setTimestamp(1_700_000_000L))
        .build();

    VehiclePositionEvent event = GtfsRealtimeEventMapper
        .vehiclePosition("wolfline", entity, 0)
        .orElseThrow();

    assertThat(event.vehicleId()).isEqualTo("bus-42");
    assertThat(event.tripId()).isEqualTo("trip-1");
    assertThat(event.currentStopSequence()).isEqualTo(4);
    assertThat(event.currentStatus()).isEqualTo("IN_TRANSIT_TO");
    assertThat(event.recordedAt()).isEqualTo(Instant.ofEpochSecond(1_700_000_000L));
  }

  @Test
  void mapsTripDelayAndNextStopPrediction() {
    FeedEntity entity = FeedEntity.newBuilder()
        .setId("trip-update-1")
        .setTripUpdate(TripUpdate.newBuilder()
            .setTrip(TripDescriptor.newBuilder().setTripId("trip-1").setRouteId("route-1"))
            .setVehicle(VehicleDescriptor.newBuilder().setId("bus-42"))
            .setDelay(420)
            .setTimestamp(1_700_000_000L)
            .addStopTimeUpdate(TripUpdate.StopTimeUpdate.newBuilder()
                .setStopId("stop-7")
                .setStopSequence(7)
                .setArrival(TripUpdate.StopTimeEvent.newBuilder().setTime(1_700_000_600L))))
        .build();

    TripUpdateEvent event = GtfsRealtimeEventMapper
        .tripUpdate("wolfline", entity, 0)
        .orElseThrow();

    assertThat(event.delaySeconds()).isEqualTo(420);
    assertThat(event.nextStopId()).isEqualTo("stop-7");
    assertThat(event.estimatedArrival()).isEqualTo(Instant.ofEpochSecond(1_700_000_600L));
  }

  @Test
  void acceptsTripUpdateWithoutDelay() {
    FeedEntity entity = FeedEntity.newBuilder()
        .setId("trip-update-2")
        .setTripUpdate(TripUpdate.newBuilder()
            .setTrip(TripDescriptor.newBuilder().setTripId("trip-2").setRouteId("route-1"))
            .setTimestamp(1_700_000_000L))
        .build();

    TripUpdateEvent event = GtfsRealtimeEventMapper
        .tripUpdate("wolfline", entity, 0)
        .orElseThrow();

    assertThat(event.delaySeconds()).isNull();
    assertThat(event.nextStopId()).isNull();
  }

  @Test
  void mapsServiceAlertTargetsAndActivePeriod() {
    FeedEntity entity = FeedEntity.newBuilder()
        .setId("alert-1")
        .setAlert(Alert.newBuilder()
            .setCause(Alert.Cause.CONSTRUCTION)
            .setEffect(Alert.Effect.DETOUR)
            .setHeaderText(translated("Route 43 detour"))
            .setDescriptionText(translated("Use the temporary stop on Hillsborough Street."))
            .addInformedEntity(EntitySelector.newBuilder().setRouteId("route-43"))
            .addInformedEntity(EntitySelector.newBuilder().setStopId("stop-7"))
            .addActivePeriod(TimeRange.newBuilder()
                .setStart(1_700_000_000L).setEnd(1_700_003_600L)))
        .build();

    ServiceAlertEvent event = GtfsRealtimeEventMapper.serviceAlert(entity).orElseThrow();

    assertThat(event.alertId()).isEqualTo("alert-1");
    assertThat(event.cause()).isEqualTo("CONSTRUCTION");
    assertThat(event.effect()).isEqualTo("DETOUR");
    assertThat(event.header()).isEqualTo("Route 43 detour");
    assertThat(event.routeIds()).containsExactly("route-43");
    assertThat(event.stopIds()).containsExactly("stop-7");
    assertThat(event.activePeriods().getFirst().endsAt())
        .isEqualTo(Instant.ofEpochSecond(1_700_003_600L));
  }

  private static TranslatedString translated(String text) {
    return TranslatedString.newBuilder()
        .addTranslation(TranslatedString.Translation.newBuilder().setText(text).setLanguage("en"))
        .build();
  }
}
