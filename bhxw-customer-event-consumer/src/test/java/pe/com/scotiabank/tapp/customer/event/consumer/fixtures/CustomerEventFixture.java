package pe.com.scotiabank.tapp.customer.event.consumer.fixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Objects;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.kafka.CustomerEventDeserializer;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionResult;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import tools.jackson.databind.node.ObjectNode;

public final class CustomerEventFixture {
  private CustomerEventFixture() {}

  public static CustomerEventEnvelope event() {
    try (var input =
            Objects.requireNonNull(
                CustomerEventFixture.class.getResourceAsStream(
                    "/customer-information-event.json"));
        var deserializer = new CustomerEventDeserializer()) {
      return deserializer.deserialize("customer", input.readAllBytes());
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  public static ObjectNode user(CustomerEventEnvelope event) {
    return (ObjectNode) event.payload().path("user_details");
  }

  public static CustomerEventEnvelope eventAt(String timestamp) {
    var event = event();
    user(event).put("event_timestamp", timestamp);
    return event;
  }

  public static CustomerSnapshot snapshot(CustomerEventEnvelope event) {
    var user = event.payload().path("user_details");
    return new CustomerSnapshot(
        event,
        event.metadata().path("source_id").asText(),
        user.path("enterprise_details").path("enterprise_sco_id").asText(),
        user.path("user_id").asText(),
        Instant.parse(user.path("event_timestamp").asText()));
  }

  public static CustomerProjectionResult projectionResult() {
    return CustomerProjectionResult.applied(
        "55421", "CUSTOMER_INFORMATION_RETRIEVED", Instant.now());
  }
}
