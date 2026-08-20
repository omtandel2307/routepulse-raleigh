package com.routepulse.ingestion;
public record VehiclePositionEvent(String agencyId,String vehicleId,String tripId,String routeId,double latitude,double longitude,float bearing,float speed,long timestamp) {}

