package pe.com.scotiabank.tapp.service.registration.api.adapters.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {

  @Bean
  Clock utcClock() {
    return Clock.systemUTC();
  }
}
