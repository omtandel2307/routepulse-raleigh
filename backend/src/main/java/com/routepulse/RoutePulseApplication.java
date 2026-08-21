package com.routepulse;

import com.routepulse.config.RoutePulseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(RoutePulseProperties.class)
public class RoutePulseApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoutePulseApplication.class, args);
    }
}
