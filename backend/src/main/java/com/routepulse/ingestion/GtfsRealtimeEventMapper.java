package com.routepulse.ingestion;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.EntitySelector;
import com.google.transit.realtime.GtfsRealtime.TimeRange;
import com.google.transit.realtime.GtfsRealtime.TranslatedString;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    Instant recordedAt = timestamp(update.hasTimestamp() ? update.getTimestamp() : 0, feedTimestamp);
    TripUpdate.StopTimeUpdate nextStop = update.getStopTimeUpdateList().stream()
        .filter(GtfsRealtimeEventMapper::hasPrediction)
        .filter(stop -> isCurrentOrFuture(stop, recordedAt))
        .findFirst()
        .orElseGet(() -> update.getStopTimeUpdateList().stream()
            .filter(GtfsRealtimeEventMapper::hasPrediction)
            .findFirst()
            .orElse(null));
    Integer delay = update.hasDelay() ? Integer.valueOf(update.getDelay()) : delay(nextStop);
    String stopId = nextStop == null ? null : blankToNull(nextStop.getStopId());
    Integer stopSequence = nextStop != null && nextStop.hasStopSequence()
        ? nextStop.getStopSequence() : null;
    Instant arrival = nextStop == null || !nextStop.hasArrival()
        ? null : eventTime(nextStop.getArrival());
    Instant departure = nextStop == null || !nextStop.hasDeparture()
        ? null : eventTime(nextStop.getDeparture());
    String vehicleId = update.hasVehicle() ? blankToNull(update.getVehicle().getId()) : null;
    return Optional.of(new TripUpdateEvent(agencyId, tripId,
        blankToNull(update.getTrip().getRouteId()), vehicleId, delay, stopId, stopSequence,
        arrival, departure, recordedAt));
  }

  public static Optional<ServiceAlertEvent> serviceAlert(FeedEntity entity) {
    if (!entity.hasAlert()) {
      return Optional.empty();
    }
    String alertId = blankToNull(entity.getId());
    if (alertId == null) {
      return Optional.empty();
    }

    Alert alert = entity.getAlert();
    List<String> routeIds = alert.getInformedEntityList().stream()
        .map(EntitySelector::getRouteId)
        .map(GtfsRealtimeEventMapper::blankToNull)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .toList();
    List<String> stopIds = alert.getInformedEntityList().stream()
        .map(EntitySelector::getStopId)
        .map(GtfsRealtimeEventMapper::blankToNull)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .toList();
    List<ServiceAlertEvent.ActivePeriod> periods = alert.getActivePeriodList().stream()
        .map(GtfsRealtimeEventMapper::activePeriod)
        .toList();
    String cause = alert.hasCause() ? alert.getCause().name() : "UNKNOWN_CAUSE";
    String effect = alert.hasEffect() ? alert.getEffect().name() : "UNKNOWN_EFFECT";
    String header = translation(alert.hasHeaderText() ? alert.getHeaderText() : null);
    if (header == null) {
      header = humanize(effect);
    }
    return Optional.of(new ServiceAlertEvent(alertId, cause, effect, header,
        translation(alert.hasDescriptionText() ? alert.getDescriptionText() : null),
        translation(alert.hasUrl() ? alert.getUrl() : null), routeIds, stopIds, periods));
  }

  private static ServiceAlertEvent.ActivePeriod activePeriod(TimeRange period) {
    Instant startsAt = period.hasStart() && period.getStart() > 0
        ? Instant.ofEpochSecond(period.getStart()) : null;
    Instant endsAt = period.hasEnd() && period.getEnd() > 0
        ? Instant.ofEpochSecond(period.getEnd()) : null;
    return new ServiceAlertEvent.ActivePeriod(startsAt, endsAt);
  }

  private static String translation(TranslatedString translated) {
    if (translated == null || translated.getTranslationCount() == 0) {
      return null;
    }
    return translated.getTranslationList().stream()
        .filter(item -> "en".equalsIgnoreCase(item.getLanguage()))
        .map(TranslatedString.Translation::getText)
        .map(GtfsRealtimeEventMapper::blankToNull)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElseGet(() -> blankToNull(translated.getTranslation(0).getText()));
  }

  private static String humanize(String value) {
    String normalized = value == null ? "SERVICE ALERT" : value.replace('_', ' ').toLowerCase();
    return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
  }

  private static boolean hasPrediction(TripUpdate.StopTimeUpdate update) {
    return update.hasArrival() || update.hasDeparture();
  }

  private static boolean isCurrentOrFuture(TripUpdate.StopTimeUpdate update, Instant recordedAt) {
    Instant predictedAt = update.hasArrival() ? eventTime(update.getArrival()) : null;
    if (predictedAt == null && update.hasDeparture()) {
      predictedAt = eventTime(update.getDeparture());
    }
    return predictedAt == null || !predictedAt.isBefore(recordedAt.minus(2, ChronoUnit.MINUTES));
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
