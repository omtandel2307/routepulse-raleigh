package com.routepulse.ingestion;

import com.google.transit.realtime.GtfsRealtime;
import com.routepulse.config.RoutePulseProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GtfsRealtimeClient {
  private final RestClient client;

  public GtfsRealtimeClient(RoutePulseProperties properties) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.ingestion().requestTimeout());
    factory.setReadTimeout(properties.ingestion().requestTimeout());
    client = RestClient.builder().defaultHeader("User-Agent", "RoutePulse-Raleigh/0.1").requestFactory(factory).build();
  }

  public GtfsRealtime.FeedMessage fetch(String url) {
    byte[] body = client.get().uri(url).retrieve().body(byte[].class);
    try {
      return GtfsRealtime.FeedMessage.parseFrom(body);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid GTFS-Realtime feed", e);
    }
  }
}
