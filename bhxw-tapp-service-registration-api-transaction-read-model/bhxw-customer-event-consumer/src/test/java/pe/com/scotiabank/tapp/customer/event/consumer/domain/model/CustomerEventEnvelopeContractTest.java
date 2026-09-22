package pe.com.scotiabank.tapp.customer.event.consumer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.kafka.CustomerEventDeserializer;
import pe.com.scotiabank.tapp.customer.event.consumer.fixtures.CustomerEventFixture;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class CustomerEventEnvelopeContractTest {
  @Test
  void shouldReadExactProducerContractAndRoundTripDynamicPayload() {
    var event = CustomerEventFixture.event();
    ((ObjectNode) event.payload()).putObject("new_section").putArray("options").add("A").add(7);
    ((ObjectNode) event.metadata()).put("new_metadata_field", "preserved");
    var json = JsonMapper.builder().build().writeValueAsBytes(event);
    try (var deserializer = new CustomerEventDeserializer()) {
      var reread = deserializer.deserialize("customer", json);
      assertThat(reread.payload()).isEqualTo(event.payload());
      assertThat(reread.metadata()).isEqualTo(event.metadata());
      assertThat(reread.payload().path("customer_identity").path("document_number").asText())
          .isEqualTo("12345678");
    }
  }

  @Test
  void shouldPreserveFinancialPrecisionAndRejectInvalidJson() {
    try (var deserializer = new CustomerEventDeserializer()) {
      var json = "{\"payload\":{\"limit\":12345678901234567890.123456789},\"metadata\":{}}";
      var event = deserializer.deserialize("customer", json.getBytes(StandardCharsets.UTF_8));
      assertThat(event.payload().path("limit").decimalValue().toPlainString())
          .isEqualTo("12345678901234567890.123456789");
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  deserializer.deserialize(
                      "customer", "{\"payload\":{},}".getBytes(StandardCharsets.UTF_8)))
          .isInstanceOf(org.apache.kafka.common.errors.SerializationException.class);
    }
  }
}
