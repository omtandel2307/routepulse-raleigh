package com.routepulse.ingestion;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

import java.time.Instant;
import java.util.Optional;

public final class GtfsRealtimeEventMapper {

  private GtfsRealtimeEventMapper() {
  }

  public static Optional<VehiclePositionEvent> vehiclePosition(
      String agencyId, FeedEntity entity, long feedTimestamp) {
    if (!entity.hasVehicle() || !entity.getVehicle().hasPosition()) {
      return Optional.empty();
    }

    VehiclePosition vehicle = entity.getVehicle();
    String vehicleId = vehicle.hasVehicle() ? blankToNull(vehicle.getVehicle().getId()) : null;
    if (vehicleId == null) {
      vehicleId = blankToNull(entity.getId());
    }
    if (vehicleId == null) {
      return Optional.empty();
    }

    var position = vehicle.getPosition();
    String tripId = vehicle.hasTrip() ? blankToNull(vehicle.getTrip().getTripId()) : null;
    String routeId = vehicle.hasTrip() ? blankToNull(vehicle.getTrip().getRouteId()) : null;
    Float bearing = position.hasBearing() ? position.getBearing() : null;
    Float speed = position.hasSpeed() ? position.getSpeed() : null;
    Integer stopSequence = vehicle.hasCurrentStopSequence() ? vehicle.getCurrentStopSequence() : null;
    String status = vehicle.hasCurrentStatus() ? vehicle.getCurrentStatus().name() : null;
    Instant recordedAt = timestamp(vehicle.hasTimestamp() ? vehicle.getTimestamp() : 0, feedTimestamp);

    return Optional.of(new VehiclePositionEvent(agencyId, vehicleId, tripId, routeId,
        position.getLatitude(), position.getLongitude(), bearing, speed, stopSequence, status, recordedAt));
  }

  public static Optional<TripUpdateEvent> tripUpdate(
      String agencyId, FeedEntity entity, long feedTimestamp) {
    if (!entity.hasTripUpdate() || !entity.getTripUpdate().hasTrip()) {
      return Optional.empty();
    }

    TripUpdate update = entity.getTripUpdate();
    String tripId = blankToNull(update.getTrip().getTripId());
    if (tripId == null) {
      return Optional.empty();
    }

    TripUpdate.StopTimeUpdate nextStop = update.getStopTimeUpdateList().stream()
        .filter(GtfsRealtimeEventMapper::hasPrediction)
        .findFirst()
        .orElse(null);
    Integer delay = update.hasDelay() ? Integer.valueOf(update.getDelay()) : delay(nextStop);
    String stopId = nextStop == null ? null : blankToNull(nextStop.getStopId());
    Integer stopSequence = nextStop != null && nextStop.hasStopSequence()
        ? nextStop.getStopSequence() : null;
    Instant arrival = nextStop == null || !nextStop.hasArrival()
        ? null : eventTime(nextStop.getArrival());
    Instant departure = nextStop == null || !nextStop.hasDeparture()
        ? null : eventTime(nextStop.getDeparture());
    String vehicleId = update.hasVehicle() ? blankToNull(update.getVehicle().getId()) : null;
    Instant recordedAt = timestamp(update.hasTimestamp() ? update.getTimestamp() : 0, feedTimestamp);

    return Optional.of(new TripUpdateEvent(agencyId, tripId,
        blankToNull(update.getTrip().getRouteId()), vehicleId, delay, stopId, stopSequence,
        arrival, departure, recordedAt));
  }

  private static boolean hasPrediction(TripUpdate.StopTimeUpdate update) {
    return update.hasArrival() || update.hasDeparture();
  }

  private static Integer delay(TripUpdate.StopTimeUpdate update) {
    if (update == null) {
      return null;
    }
    if (update.hasArrival() && update.getArrival().hasDelay()) {
      return update.getArrival().getDelay();
    }
    if (update.hasDeparture() && update.getDeparture().hasDelay()) {
      return update.getDeparture().getDelay();
    }
    return null;
  }

  private static Instant eventTime(StopTimeEvent event) {
    return event.hasTime() && event.getTime() > 0 ? Instant.ofEpochSecond(event.getTime()) : null;
  }

  private static Instant timestamp(long entityTimestamp, long feedTimestamp) {
    long epochSeconds = entityTimestamp > 0 ? entityTimestamp : feedTimestamp;
    return epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : Instant.now();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
