package com.routepulse.schedule;

import com.routepulse.config.RoutePulseProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GtfsScheduleClient {

  private final RestClient client;

  public GtfsScheduleClient(RoutePulseProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.ingestion().requestTimeout());
    requestFactory.setReadTimeout(properties.ingestion().requestTimeout());
    client = RestClient.builder()
        .defaultHeader("User-Agent", "RoutePulse-Raleigh/0.2")
        .requestFactory(requestFactory)
        .build();
  }

  public byte[] download(String url) {
    byte[] archive = client.get().uri(url).retrieve().body(byte[].class);
    if (archive == null || archive.length == 0) {
      throw new IllegalStateException("GTFS schedule download returned an empty archive");
    }
    return archive;
  }
}
