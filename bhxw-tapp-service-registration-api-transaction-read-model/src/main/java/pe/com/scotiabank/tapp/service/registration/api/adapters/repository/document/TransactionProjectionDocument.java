package pe.com.scotiabank.tapp.service.registration.api.adapters.repository.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.DeliveryStatus;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.EventType;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.LogAuditStatus;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionStatus;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionProjectionDocument {

  private String id;
  private String transactionId;
  private String reqMsgId;
  private TransactionStatus transactionStatus;
  private DeliveryStatus callbackStatus;
  private DeliveryStatus notificationStatus;
  private LogAuditStatus logAuditStatus;
  private BigDecimal amount;
  private String currency;
  private String accountReference;
  private String maskedAccount;
  private Instant startedAt;
  private Instant completedAt;
  private String lastEventId;
  private EventType lastEventType;
  private long aggregateVersion;
  private Instant lastEventOccurredAt;
  private List<ProjectionEventDocument> events;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant expiresAt;
}
