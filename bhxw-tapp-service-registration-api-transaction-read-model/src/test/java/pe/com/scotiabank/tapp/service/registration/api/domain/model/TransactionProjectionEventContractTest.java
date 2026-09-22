package pe.com.scotiabank.tapp.service.registration.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.LooseCoupling")
class TransactionProjectionEventContractTest {

  @Test
  void shouldExposePayloadAndMetadataAsMessageEnvelope() {
    assertThat(
            Arrays.stream(TransactionProjectionEvent.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("payload", "metadata");
  }

  @Test
  void shouldMapPayloadPropertiesUsingSnakeCase() {
    Map<String, String> jsonNames = jsonPropertyNames(TransactionProjectionPayload.class);

    assertThat(jsonNames)
        .containsEntry("transactionId", "transaction_id")
        .containsEntry("reqMsgId", "req_msg_id")
        .containsEntry("eventType", "event_type")
        .containsEntry("aggregateVersion", "aggregate_version")
        .containsEntry("occurredAt", "occurred_at")
        .containsEntry("transactionStatus", "transaction_status")
        .containsEntry("callbackStatus", "callback_status")
        .containsEntry("notificationStatus", "notification_status")
        .containsEntry("logAuditStatus", "log_audit_status")
        .containsEntry("accountReference", "account_reference")
        .containsEntry("maskedAccount", "masked_account")
        .containsEntry("startedAt", "started_at")
        .containsEntry("completedAt", "completed_at");
  }

  @Test
  void shouldMapMetadataPropertiesUsingSnakeCase() {
    Map<String, String> jsonNames = jsonPropertyNames(EventMetadata.class);

    assertThat(jsonNames)
        .containsEntry("eventId", "event_id")
        .containsEntry("schemaVersion", "schema_version")
        .containsEntry("publishedAt", "published_at")
        .containsEntry("sourceService", "source_service")
        .containsEntry("correlationId", "correlation_id")
        .containsEntry("traceId", "trace_id")
        .containsEntry("payloadSignature", "payload_signature")
        .containsEntry("keyId", "key_id")
        .containsEntry("retryCount", "retry_count");
  }

  private Map<String, String> jsonPropertyNames(Class<?> recordType) {
    return Arrays.stream(recordType.getRecordComponents())
        .collect(
            Collectors.toMap(
                RecordComponent::getName,
                component -> component.getAccessor().getAnnotation(JsonProperty.class).value()));
  }
}
