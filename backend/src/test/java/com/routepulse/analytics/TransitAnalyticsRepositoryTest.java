package com.routepulse.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransitAnalyticsRepositoryTest {

  @Test
  void usesInclusiveFiveMinuteReliabilityWindow() {
    assertThat(TransitAnalyticsRepository.isOnTime(-301)).isFalse();
    assertThat(TransitAnalyticsRepository.isOnTime(-300)).isTrue();
    assertThat(TransitAnalyticsRepository.isOnTime(0)).isTrue();
    assertThat(TransitAnalyticsRepository.isOnTime(300)).isTrue();
    assertThat(TransitAnalyticsRepository.isOnTime(301)).isFalse();
  }
}
