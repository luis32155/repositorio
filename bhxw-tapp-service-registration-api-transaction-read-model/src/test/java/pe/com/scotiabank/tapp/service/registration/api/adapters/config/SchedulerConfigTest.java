package pe.com.scotiabank.tapp.service.registration.api.adapters.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulerConfigTest {

  @Test
  void shouldCreateInitializedTaskScheduler() {
    var scheduler = new SchedulerConfig().taskScheduler();

    assertThat(scheduler).isInstanceOf(ThreadPoolTaskScheduler.class);

    ((ThreadPoolTaskScheduler) scheduler).shutdown();
  }
}
