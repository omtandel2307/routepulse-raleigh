package com.routepulse.live;

import org.junit.jupiter.api.Test;

import static com.routepulse.live.LiveTransitRepository.DelayStatus.EARLY;
import static com.routepulse.live.LiveTransitRepository.DelayStatus.LATE;
import static com.routepulse.live.LiveTransitRepository.DelayStatus.ON_TIME;
import static com.routepulse.live.LiveTransitRepository.DelayStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

class LiveTransitRepositoryTest {

  @Test
  void classifiesDelayUsingFiveMinuteThreshold() {
    assertThat(LiveTransitRepository.delayStatus(null)).isEqualTo(UNKNOWN);
    assertThat(LiveTransitRepository.delayStatus(-301)).isEqualTo(EARLY);
    assertThat(LiveTransitRepository.delayStatus(-300)).isEqualTo(ON_TIME);
    assertThat(LiveTransitRepository.delayStatus(300)).isEqualTo(ON_TIME);
    assertThat(LiveTransitRepository.delayStatus(301)).isEqualTo(LATE);
  }
}
