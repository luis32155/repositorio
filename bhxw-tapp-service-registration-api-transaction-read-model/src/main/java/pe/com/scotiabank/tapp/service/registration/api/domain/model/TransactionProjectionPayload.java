package pe.com.scotiabank.tapp.service.registration.api.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionProjectionPayload(
    @JsonProperty("transaction_id") @NotBlank @Size(max = 100) String transactionId,
    @JsonProperty("req_msg_id") @NotBlank @Size(max = 100) String reqMsgId,
    @JsonProperty("event_type") @NotNull EventType eventType,
    @JsonProperty("aggregate_version") @Positive long aggregateVersion,
    @JsonProperty("occurred_at") @NotNull Instant occurredAt,
    @JsonProperty("transaction_status") @NotNull TransactionStatus transactionStatus,
    @JsonProperty("callback_status") @NotNull DeliveryStatus callbackStatus,
    @JsonProperty("notification_status") @NotNull DeliveryStatus notificationStatus,
    @JsonProperty("log_audit_status") @NotNull LogAuditStatus logAuditStatus,
    @JsonProperty("amount") @DecimalMin("0.00") @Digits(integer = 14, fraction = 4)
        BigDecimal amount,
    @JsonProperty("currency") @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @JsonProperty("account_reference") @Size(max = 200) String accountReference,
    @JsonProperty("masked_account") @Size(max = 50) String maskedAccount,
    @JsonProperty("started_at") @NotNull Instant startedAt,
    @JsonProperty("completed_at") Instant completedAt,
    @JsonProperty("events") @Size(max = 200) List<@Valid ProjectionEventSummary> events) {

  @SuppressWarnings({"PMD.UnusedAssignment", "PMD.NullAssignment"})
  public TransactionProjectionPayload {
    events = events == null ? null : List.copyOf(events);
  }

  public String documentId() {
    return transactionId + "|" + reqMsgId;
  }
}
