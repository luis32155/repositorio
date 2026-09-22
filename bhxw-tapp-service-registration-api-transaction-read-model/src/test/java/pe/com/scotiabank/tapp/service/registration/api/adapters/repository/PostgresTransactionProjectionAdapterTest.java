package pe.com.scotiabank.tapp.service.registration.api.adapters.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.entity.TransactionProjectionEntity;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.model.ExistingProjection;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.table.TransactionProjectionTableRepository;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.ProjectionPersistenceException;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.VersionConflictException;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionResultType;
import pe.com.scotiabank.tapp.service.registration.api.fixtures.TransactionProjectionEventFixture;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class PostgresTransactionProjectionAdapterTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:05:02Z");
  private static final Instant EXPIRES_AT = Instant.parse("2027-01-01T10:05:00Z");

  @Mock private TransactionProjectionTableRepository projections;
  private PostgresTransactionProjectionAdapter adapter;

  @Test
  void shouldExposeReactiveEntityIdentifier() {
    var identifier = "transaction|request";
    assertThat(new TransactionProjectionEntity(identifier).documentId()).isEqualTo(identifier);
  }

  @BeforeEach
  void setUp() {
    adapter =
        new PostgresTransactionProjectionAdapter(
            projections,
            JsonMapper.builder().findAndAddModules().build(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void shouldApplyProjectionUsingConditionalPostgresUpsert() {
    var event = TransactionProjectionEventFixture.completedEvent();
    given(projections.upsertIfNewer(anyString(), anyString(), anyString(), any(), any()))
        .willReturn(Mono.just(event.payload().aggregateVersion()));

    StepVerifier.create(adapter.upsert(event, EXPIRES_AT))
        .assertNext(
            result -> {
              assertThat(result.result()).isEqualTo(ProjectionResultType.APPLIED);
              assertThat(result.currentVersion()).isEqualTo(event.payload().aggregateVersion());
            })
        .verifyComplete();

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(projections)
        .upsertIfNewer(
            org.mockito.ArgumentMatchers.eq(event.payload().documentId()),
            org.mockito.ArgumentMatchers.eq(event.metadata().eventId()),
            payload.capture(),
            org.mockito.ArgumentMatchers.eq(NOW),
            org.mockito.ArgumentMatchers.eq(EXPIRES_AT));
    assertThat(payload.getValue()).contains("\"transaction_id\"");
  }

  @Test
  void shouldClassifyDuplicateAndOutdatedEvents() {
    var event = TransactionProjectionEventFixture.completedEvent();
    given(projections.upsertIfNewer(anyString(), anyString(), anyString(), any(), any()))
        .willReturn(Mono.empty());
    given(projections.findState(event.payload().documentId()))
        .willReturn(
            Mono.just(
                new ExistingProjection(
                    event.metadata().eventId(), event.payload().aggregateVersion())));

    StepVerifier.create(adapter.upsert(event, EXPIRES_AT))
        .assertNext(
            result -> assertThat(result.result()).isEqualTo(ProjectionResultType.DUPLICATE_IGNORED))
        .verifyComplete();

    given(projections.findState(event.payload().documentId()))
        .willReturn(
            Mono.just(
                new ExistingProjection("newer-event", event.payload().aggregateVersion() + 1)));
    StepVerifier.create(adapter.upsert(event, EXPIRES_AT))
        .assertNext(
            result -> assertThat(result.result()).isEqualTo(ProjectionResultType.OUTDATED_IGNORED))
        .verifyComplete();
  }

  @Test
  void shouldRejectSameVersionWithDifferentEventId() {
    var event = TransactionProjectionEventFixture.completedEvent();
    given(projections.upsertIfNewer(anyString(), anyString(), anyString(), any(), any()))
        .willReturn(Mono.empty());
    given(projections.findState(event.payload().documentId()))
        .willReturn(
            Mono.just(
                new ExistingProjection("different-event", event.payload().aggregateVersion())));

    StepVerifier.create(adapter.upsert(event, EXPIRES_AT))
        .expectError(VersionConflictException.class)
        .verify();
  }

  @Test
  void shouldMapDatabaseFailureAndMissingConflictState() {
    var event = TransactionProjectionEventFixture.completedEvent();
    var failure = new DataAccessResourceFailureException("PostgreSQL unavailable");
    given(projections.upsertIfNewer(anyString(), anyString(), anyString(), any(), any()))
        .willReturn(Mono.error(failure));
    StepVerifier.create(adapter.upsert(event, EXPIRES_AT))
        .expectErrorMatches(
            error -> error instanceof ProjectionPersistenceException && error.getCause() == failure)
        .verify();

    given(projections.upsertIfNewer(anyString(), anyString(), anyString(), any(), any()))
        .willReturn(Mono.empty());
    given(projections.findState(event.payload().documentId())).willReturn(Mono.empty());
    StepVerifier.create(adapter.upsert(event, EXPIRES_AT))
        .expectError(ProjectionPersistenceException.class)
        .verify();
  }

  @Test
  void shouldRequestRetryForUnclassifiableConcurrentState() {
    var event = TransactionProjectionEventFixture.completedEvent();
    given(projections.upsertIfNewer(anyString(), anyString(), anyString(), any(), any()))
        .willReturn(Mono.empty());
    given(projections.findState(event.payload().documentId()))
        .willReturn(
            Mono.just(
                new ExistingProjection("older-event", event.payload().aggregateVersion() - 1)));

    StepVerifier.create(adapter.upsert(event, EXPIRES_AT))
        .expectError(ProjectionPersistenceException.class)
        .verify();
  }
}
