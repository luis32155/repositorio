package pe.com.scotiabank.tapp.customer.event.consumer.domain.model;

import java.time.Instant;

public record CustomerProjectionResult(
    String customerKey, CustomerProjectionStatus status, Instant processedAt, String eventId) {

  public static CustomerProjectionResult applied(
      String customerKey, String eventId, Instant processedAt) {
    return new CustomerProjectionResult(
        customerKey, CustomerProjectionStatus.APPLIED, processedAt, eventId);
  }

  public static CustomerProjectionResult ignored(
      String customerKey, String eventId, Instant processedAt) {
    return new CustomerProjectionResult(
        customerKey, CustomerProjectionStatus.IGNORED, processedAt, eventId);
  }
}
