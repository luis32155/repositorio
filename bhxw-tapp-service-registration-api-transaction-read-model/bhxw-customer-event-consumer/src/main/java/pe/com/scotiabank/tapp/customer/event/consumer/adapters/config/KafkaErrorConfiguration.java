package pe.com.scotiabank.tapp.customer.event.consumer.adapters.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;

@Configuration
@Slf4j
public class KafkaErrorConfiguration {
  @Bean
  public KafkaListenerErrorHandler customerListenerErrorHandler(
      ObjectProvider<KafkaListenerEndpointRegistry> registries) {
    return (message, exception) -> {
      var container = registries.getObject().getListenerContainer("customerEvents");
      if (container != null) {
        container.stop(() -> {});
      }
      if (log.isErrorEnabled()) {
        log.error(
            "Customer consumer stopped with uncommitted record topic={} partition={} offset={}",
            message.getHeaders().get(KafkaHeaders.RECEIVED_TOPIC),
            message.getHeaders().get(KafkaHeaders.RECEIVED_PARTITION),
            message.getHeaders().get(KafkaHeaders.OFFSET));
      }
      // Spring Kafka 4.0.x acknowledges an async failure if the handler throws.
      // Returning null sends no reply/ack; stopping leaves the record available after restart.
      return null;
    };
  }

  @Bean
  public CommonErrorHandler customerKafkaErrorHandler() {
    // With one topic, stop on failure instead of silently discarding the event.
    return new CommonContainerStoppingErrorHandler();
  }
}
