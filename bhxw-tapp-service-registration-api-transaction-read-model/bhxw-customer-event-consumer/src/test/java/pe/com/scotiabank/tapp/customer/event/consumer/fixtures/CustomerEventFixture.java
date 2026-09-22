package pe.com.scotiabank.tapp.customer.event.consumer.fixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Objects;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.kafka.CustomerEventDeserializer;
import pe.com.scotiabank.tapp.customer.event.consumer.application.mapping.CustomerSnapshotMapper;
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

  public static ObjectNode payload(CustomerEventEnvelope event) {
    return (ObjectNode) event.payload();
  }

  public static ObjectNode identity(CustomerEventEnvelope event) {
    return (ObjectNode) event.payload().path("customer_identity");
  }

  public static CustomerEventEnvelope eventAt(String timestamp, long sequence) {
    var event = event();
    ((ObjectNode) event.metadata())
        .put("event_timestamp", timestamp)
        .put("source_sequence", sequence);
    return event;
  }

  public static CustomerSnapshot snapshot(CustomerEventEnvelope event) {
    return new CustomerSnapshotMapper().map(event);
  }

  public static CustomerProjectionResult projectionResult() {
    return CustomerProjectionResult.applied(
        "604|01|12345678", "evt-20260922-000001", Instant.now());
  }
}
