package pe.com.scotiabank.tapp.service.registration.api.application.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.validation.Validation;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.scotiabank.tapp.service.registration.api.application.ports.output.TransactionProjectionCachePort;
import pe.com.scotiabank.tapp.service.registration.api.application.ports.output.TransactionProjectionRepositoryPort;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.InvalidProjectionEventException;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.ProjectionPersistenceException;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.EventMetadata;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionResult;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionPayload;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionStatus;
import pe.com.scotiabank.tapp.service.registration.api.fixtures.TransactionProjectionEventFixture;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({
  "PMD.UnitTestContainsTooManyAsserts",
  "PMD.UnitTestAssertionsShouldIncludeMessage"
})
class TransactionProjectionServiceTest {

  @Mock private TransactionProjectionRepositoryPort repository;

  @Mock private TransactionProjectionCachePort cache;

  private TransactionProjectionService service;

  @BeforeEach
  void setUp() {
    var validator = Validation.buildDefaultValidatorFactory().getValidator();
    service = new TransactionProjectionService(repository, cache, validator);
  }

  @Test
  void shouldProjectValidCompletedEventAndCalculateOneYearRetention() {
    var event = TransactionProjectionEventFixture.completedEvent();
    var result = ProjectionResult.applied(event);
    Instant expectedExpiration = Instant.parse("2027-01-01T10:05:00Z");
    given(repository.upsert(eq(event), eq(expectedExpiration))).willReturn(Mono.just(result));
    given(cache.put(eq(event), eq(expectedExpiration))).willReturn(Mono.empty());

    StepVerifier.create(service.project(event)).expectNext(result).verifyComplete();

    verify(repository).upsert(event, expectedExpiration);
    verify(cache).put(event, expectedExpiration);
  }

  @Test
  void shouldProjectProcessingEventWithoutExpiration() {
    var event = TransactionProjectionEventFixture.processingEvent();
    var result = ProjectionResult.applied(event);
    given(repository.upsert(eq(event), isNull())).willReturn(Mono.just(result));
    given(cache.put(eq(event), isNull())).willReturn(Mono.empty());

    StepVerifier.create(service.project(event)).expectNext(result).verifyComplete();

    verify(repository).upsert(event, null);
    verify(cache).put(event, null);
  }

  @Test
  void shouldNotCacheWhenResultIsNotApplied() {
    var event = TransactionProjectionEventFixture.completedEvent();
    var result = ProjectionResult.duplicate(event, event.payload().aggregateVersion());
    Instant expectedExpiration = Instant.parse("2027-01-01T10:05:00Z");
    given(repository.upsert(eq(event), eq(expectedExpiration))).willReturn(Mono.just(result));

    StepVerifier.create(service.project(event)).expectNext(result).verifyComplete();

    verify(cache, never())
        .put(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldRejectNullEvent() {
    StepVerifier.create(service.project(null))
        .expectErrorMatches(
            error ->
                error instanceof InvalidProjectionEventException
                    && error.getMessage().contains("obligatorio"))
        .verify();

    verify(repository, never())
        .upsert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldRejectEventWithBeanValidationErrors() {
    var valid = TransactionProjectionEventFixture.completedEvent();
    var payload = copyPayload(valid.payload(), " ", valid.payload().completedAt());
    var invalidEvent = TransactionProjectionEventFixture.withPayload(valid, payload);

    StepVerifier.create(service.project(invalidEvent))
        .expectErrorMatches(
            error ->
                error instanceof InvalidProjectionEventException
                    && error.getMessage().contains("transactionId"))
        .verify();

    verify(repository, never())
        .upsert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldRejectInvalidMetadataSchemaVersion() {
    var valid = TransactionProjectionEventFixture.completedEvent();
    EventMetadata metadata = valid.metadata();
    var invalidMetadata =
        new EventMetadata(
            metadata.eventId(),
            "version-one",
            metadata.publishedAt(),
            metadata.sourceService(),
            metadata.correlationId(),
            metadata.traceId(),
            metadata.payloadSignature(),
            metadata.keyId(),
            metadata.retryCount());
    var invalidEvent = TransactionProjectionEventFixture.withMetadata(valid, invalidMetadata);

    StepVerifier.create(service.project(invalidEvent))
        .expectError(InvalidProjectionEventException.class)
        .verify();

    verify(repository, never())
        .upsert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldRejectCompletedTransactionWithoutCompletedAt() {
    var valid = TransactionProjectionEventFixture.completedEvent();
    var invalidPayload = copyPayload(valid.payload(), valid.payload().transactionId(), null);
    var invalidEvent = TransactionProjectionEventFixture.withPayload(valid, invalidPayload);

    StepVerifier.create(service.project(invalidEvent))
        .expectErrorMatches(
            error ->
                error instanceof InvalidProjectionEventException
                    && error.getMessage().contains("completed_at es obligatorio"))
        .verify();

    verify(repository, never())
        .upsert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldRejectCompletedAtBeforeStartedAt() {
    var valid = TransactionProjectionEventFixture.completedEvent();
    Instant invalidCompletedAt = valid.payload().startedAt().minusSeconds(1);
    var invalidPayload =
        copyPayload(valid.payload(), valid.payload().transactionId(), invalidCompletedAt);
    var invalidEvent = TransactionProjectionEventFixture.withPayload(valid, invalidPayload);

    StepVerifier.create(service.project(invalidEvent))
        .expectErrorMatches(
            error ->
                error instanceof InvalidProjectionEventException
                    && error.getMessage().contains("anterior a started_at"))
        .verify();

    verify(repository, never())
        .upsert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldPropagatePersistenceErrorFromRepository() {
    var event = TransactionProjectionEventFixture.completedEvent();
    Instant expiration = Instant.parse("2027-01-01T10:05:00Z");
    var expectedError = new ProjectionPersistenceException("PostgreSQL no disponible");
    given(repository.upsert(event, expiration)).willReturn(Mono.error(expectedError));

    StepVerifier.create(service.project(event))
        .expectErrorSatisfies(
            error -> org.junit.jupiter.api.Assertions.assertSame(expectedError, error))
        .verify();
  }

  private TransactionProjectionPayload copyPayload(
      TransactionProjectionPayload source, String transactionId, Instant completedAt) {
    return new TransactionProjectionPayload(
        transactionId,
        source.reqMsgId(),
        source.eventType(),
        source.aggregateVersion(),
        source.occurredAt(),
        TransactionStatus.COMPLETED,
        source.callbackStatus(),
        source.notificationStatus(),
        source.logAuditStatus(),
        source.amount(),
        source.currency(),
        source.accountReference(),
        source.maskedAccount(),
        source.startedAt(),
        completedAt,
        source.events());
  }
}
