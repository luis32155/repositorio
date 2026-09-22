package pe.com.scotiabank.tapp.customer.event.consumer.adapters.kafka;

import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Preserve decimal precision and dynamic fields before projection. */
public class CustomerEventDeserializer extends JacksonJsonDeserializer<CustomerEventEnvelope> {
  public CustomerEventDeserializer() {
    super(
        CustomerEventEnvelope.class,
        JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build(),
        false);
  }
}
