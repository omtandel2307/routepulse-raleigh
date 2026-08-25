package com.routepulse.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
  public static final String VEHICLE_POSITIONS_TOPIC = "vehicle.positions";
  public static final String TRIP_UPDATES_TOPIC = "trip.updates";
  public static final String SERVICE_ALERTS_TOPIC = "service.alerts";

  @Bean
  NewTopic vehiclePositionsTopic() {
    return TopicBuilder.name(VEHICLE_POSITIONS_TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic tripUpdatesTopic() {
    return TopicBuilder.name(TRIP_UPDATES_TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic serviceAlertsTopic() {
    return TopicBuilder.name(SERVICE_ALERTS_TOPIC).partitions(1).replicas(1).build();
  }
}
