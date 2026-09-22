package pe.com.scotiabank.tapp.customer.event.consumer.domain.model;

import java.time.Instant;

public record CustomerProjectionResult(
    String userId, CustomerProjectionStatus status, Instant processedAt, String eventId) {

  public static CustomerProjectionResult applied(
      String userId, String eventId, Instant processedAt) {
    return new CustomerProjectionResult(
        userId, CustomerProjectionStatus.APPLIED, processedAt, eventId);
  }

  public static CustomerProjectionResult ignored(
      String userId, String eventId, Instant processedAt) {
    return new CustomerProjectionResult(
        userId, CustomerProjectionStatus.IGNORED, processedAt, eventId);
  }
}
