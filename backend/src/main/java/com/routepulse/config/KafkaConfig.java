package com.routepulse.config;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
@Configuration
public class KafkaConfig {
  public static final String VEHICLE_POSITIONS_TOPIC="vehicle.positions";
  @Bean NewTopic vehiclePositionsTopic(){ return TopicBuilder.name(VEHICLE_POSITIONS_TOPIC).partitions(3).replicas(1).build(); }
}

