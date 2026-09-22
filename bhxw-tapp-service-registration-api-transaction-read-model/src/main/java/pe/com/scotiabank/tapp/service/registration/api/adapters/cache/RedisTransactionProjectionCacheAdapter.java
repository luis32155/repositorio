package pe.com.scotiabank.tapp.service.registration.api.adapters.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.document.ProjectionEventDocument;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.document.TransactionProjectionDocument;
import pe.com.scotiabank.tapp.service.registration.api.application.ports.output.TransactionProjectionCachePort;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionEventSummary;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionEvent;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class RedisTransactionProjectionCacheAdapter implements TransactionProjectionCachePort {

  private static final Logger log =
      LoggerFactory.getLogger(RedisTransactionProjectionCacheAdapter.class);
  private static final String KEY_PREFIX = "transaction_projection:";

  private final ReactiveRedisTemplate<String, TransactionProjectionDocument> redisTemplate;
  private final Clock clock;

  @Override
  public Mono<Void> put(TransactionProjectionEvent event, Instant expiresAt) {
    String documentId = event.payload().documentId();
    String key = KEY_PREFIX + documentId;
    TransactionProjectionDocument document = toDocument(event, expiresAt);

    Mono<Boolean> write =
        expiresAt != null
            ? redisTemplate.opsForValue().set(key, document, ttlUntil(expiresAt))
            : redisTemplate.opsForValue().set(key, document);

    return write
        .doOnError(
            error ->
                log.warn(
                    "No se pudo cachear la proyección {} en Redis: {}",
                    documentId,
                    error.getMessage()))
        .onErrorResume(error -> Mono.empty())
        .then();
  }

  private Duration ttlUntil(Instant expiresAt) {
    Duration ttl = Duration.between(clock.instant(), expiresAt);
    return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
  }

  private TransactionProjectionDocument toDocument(
      TransactionProjectionEvent event, Instant expiresAt) {
    var payload = event.payload();
    var metadata = event.metadata();

    return TransactionProjectionDocument.builder()
        .id(payload.documentId())
        .transactionId(payload.transactionId())
        .reqMsgId(payload.reqMsgId())
        .transactionStatus(payload.transactionStatus())
        .callbackStatus(payload.callbackStatus())
        .notificationStatus(payload.notificationStatus())
        .logAuditStatus(payload.logAuditStatus())
        .amount(payload.amount())
        .currency(payload.currency())
        .accountReference(payload.accountReference())
        .maskedAccount(payload.maskedAccount())
        .startedAt(payload.startedAt())
        .completedAt(payload.completedAt())
        .lastEventId(metadata.eventId())
        .lastEventType(payload.eventType())
        .aggregateVersion(payload.aggregateVersion())
        .lastEventOccurredAt(payload.occurredAt())
        .events(mapEvents(payload.events()))
        .updatedAt(clock.instant())
        .expiresAt(expiresAt)
        .build();
  }

  private List<ProjectionEventDocument> mapEvents(List<ProjectionEventSummary> events) {
    if (events == null) {
      return Collections.emptyList();
    }
    return events.stream()
        .map(
            summary ->
                ProjectionEventDocument.builder()
                    .eventId(summary.eventId())
                    .eventType(summary.eventType())
                    .aggregateVersion(summary.aggregateVersion())
                    .occurredAt(summary.occurredAt())
                    .build())
        .toList();
  }
}
