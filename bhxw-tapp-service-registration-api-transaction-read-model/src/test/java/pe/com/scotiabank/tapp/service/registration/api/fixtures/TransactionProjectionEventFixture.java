package pe.com.scotiabank.tapp.service.registration.api.fixtures;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.DeliveryStatus;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.EventMetadata;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.EventType;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.LogAuditStatus;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionEventSummary;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionEvent;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionPayload;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionStatus;

public final class TransactionProjectionEventFixture {

  public static final String TRANSACTION_ID = "TXN-001";
  public static final String REQ_MSG_ID = "ABC-001";
  public static final String EVENT_ID = "EVT-000009";
  public static final long AGGREGATE_VERSION = 9L;
  public static final Instant STARTED_AT = Instant.parse("2026-01-01T10:00:00Z");
  public static final Instant COMPLETED_AT = Instant.parse("2026-01-01T10:05:00Z");
  public static final Instant PUBLISHED_AT = Instant.parse("2026-01-01T10:05:01Z");

  private TransactionProjectionEventFixture() {}

  public static TransactionProjectionEvent completedEvent() {
    return event(
        TRANSACTION_ID,
        REQ_MSG_ID,
        EVENT_ID,
        EventType.LogAuditDelivered,
        AGGREGATE_VERSION,
        TransactionStatus.COMPLETED,
        COMPLETED_AT,
        COMPLETED_AT);
  }

  public static TransactionProjectionEvent processingEvent() {
    return event(
        TRANSACTION_ID,
        REQ_MSG_ID,
        "EVT-000001",
        EventType.DebitRequested,
        1L,
        TransactionStatus.PROCESSING,
        STARTED_AT,
        null);
  }

  public static TransactionProjectionEvent event(
      String transactionId,
      String reqMsgId,
      String eventId,
      EventType eventType,
      long aggregateVersion,
      TransactionStatus transactionStatus,
      Instant occurredAt,
      Instant completedAt) {
    var payload =
        new TransactionProjectionPayload(
            transactionId,
            reqMsgId,
            eventType,
            aggregateVersion,
            occurredAt,
            transactionStatus,
            deliveryStatus(transactionStatus),
            deliveryStatus(transactionStatus),
            auditStatus(transactionStatus),
            new BigDecimal("100.00"),
            "PEN",
            "encrypted-account-reference",
            "****3456",
            STARTED_AT,
            completedAt,
            List.of(new ProjectionEventSummary(eventId, eventType, aggregateVersion, occurredAt)));

    var metadata =
        new EventMetadata(
            eventId,
            "1.0",
            PUBLISHED_AT,
            "outbox_publisher_service",
            "correlation-001",
            "trace-001",
            "payload-signature",
            "projection-signing-key-01",
            0);

    return new TransactionProjectionEvent(payload, metadata);
  }

  public static TransactionProjectionEvent withPayload(
      TransactionProjectionEvent source, TransactionProjectionPayload payload) {
    return new TransactionProjectionEvent(payload, source.metadata());
  }

  public static TransactionProjectionEvent withMetadata(
      TransactionProjectionEvent source, EventMetadata metadata) {
    return new TransactionProjectionEvent(source.payload(), metadata);
  }

  private static DeliveryStatus deliveryStatus(TransactionStatus transactionStatus) {
    return transactionStatus == TransactionStatus.COMPLETED
        ? DeliveryStatus.DELIVERED
        : DeliveryStatus.PENDING;
  }

  private static LogAuditStatus auditStatus(TransactionStatus transactionStatus) {
    return transactionStatus == TransactionStatus.COMPLETED
        ? LogAuditStatus.PERSISTED
        : LogAuditStatus.PENDING;
  }
}
