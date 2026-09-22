package pe.com.scotiabank.tapp.service.registration.api.adapters.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigurationTest {

  @Test
  void shouldProvideUtcClock() {
    var configuration = new ClockConfiguration();

    var clock = configuration.utcClock();

    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }
}
