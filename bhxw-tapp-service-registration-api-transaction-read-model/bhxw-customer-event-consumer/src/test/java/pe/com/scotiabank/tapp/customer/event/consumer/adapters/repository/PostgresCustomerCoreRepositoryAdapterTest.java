package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.reactive.TransactionalOperator;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerAccountConsolidationTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerAccountLinkTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerAccountProfileTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerDigitalContactTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerIdentityTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerIndividualTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerLegalEntityTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.CustomerPersistenceException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionStatus;
import pe.com.scotiabank.tapp.customer.event.consumer.fixtures.CustomerEventFixture;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class PostgresCustomerCoreRepositoryAdapterTest {
  private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

  @Mock private TransactionalOperator transactions;
  @Mock private PostgresAdvisoryLockRepository locks;
  @Mock private CustomerIdentityTableRepository identities;
  @Mock private CustomerIndividualTableRepository individuals;
  @Mock private CustomerLegalEntityTableRepository legalEntities;
  @Mock private CustomerDigitalContactTableRepository contacts;
  @Mock private CustomerAccountProfileTableRepository accountProfiles;
  @Mock private CustomerAccountLinkTableRepository accountLinks;
  @Mock private CustomerAccountConsolidationTableRepository consolidations;
  private PostgresCustomerCoreRepositoryAdapter repository;

  @BeforeEach
  void setUp() {
    given(transactions.transactional(any(Mono.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    repository =
        new PostgresCustomerCoreRepositoryAdapter(
            transactions,
            Clock.fixed(NOW, ZoneOffset.UTC),
            locks,
            identities,
            individuals,
            legalEntities,
            contacts,
            accountProfiles,
            accountLinks,
            consolidations);
  }

  @Test
  void shouldReplaceAllSnapshotSections() {
    arrangeSuccessfulPersistence();
    var snapshot = CustomerEventFixture.snapshot(CustomerEventFixture.event());

    StepVerifier.create(repository.replace(snapshot))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(CustomerProjectionStatus.APPLIED);
              assertThat(result.processedAt()).isEqualTo(NOW);
              assertThat(result.customerKey()).isEqualTo("604|01|12345678");
            })
        .verifyComplete();

    verify(accountProfiles, times(3)).upsert(anyString(), anyString(), any(), any());
    verify(contacts, times(2)).insert(anyString(), any(), any());
    verify(accountLinks, times(2)).insert(anyString(), any(), any());
    verify(consolidations).insert(anyString(), any(), any());
    verify(individuals).insert(snapshot, NOW);
    verify(legalEntities, never()).insert(any(), any());
  }

  @Test
  void shouldInsertLegalProfileWhenIndividualIsAbsent() {
    arrangeSuccessfulPersistence();
    var event = CustomerEventFixture.event();
    CustomerEventFixture.payload(event).putNull("individual_profile");
    CustomerEventFixture.payload(event)
        .putObject("legal_entity_profile")
        .put("country_code", "604")
        .put("document_type", "01")
        .put("document_number", "12345678")
        .put("legal_name", "ACME SAC");
    var snapshot = CustomerEventFixture.snapshot(event);

    StepVerifier.create(repository.replace(snapshot)).expectNextCount(1).verifyComplete();

    verify(individuals, never()).insert(any(), any());
    verify(legalEntities).insert(snapshot, NOW);
  }

  @Test
  void shouldIgnoreDuplicateWithoutReplacingChildren() {
    given(locks.acquire(anyString())).willReturn(Mono.empty());
    given(identities.upsertIfNewer(any(), any())).willReturn(Mono.empty());

    StepVerifier.create(
            repository.replace(CustomerEventFixture.snapshot(CustomerEventFixture.event())))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(CustomerProjectionStatus.IGNORED))
        .verifyComplete();

    verify(accountProfiles, never()).upsert(anyString(), anyString(), any(), any());
  }

  @Test
  void shouldWrapUnexpectedDatabaseFailure() {
    arrangeSuccessfulPersistence();
    given(contacts.insert(anyString(), any(), any()))
        .willReturn(Mono.error(new IllegalStateException("database offline")));

    StepVerifier.create(
            repository.replace(CustomerEventFixture.snapshot(CustomerEventFixture.event())))
        .expectError(CustomerPersistenceException.class)
        .verify();
  }

  private void arrangeSuccessfulPersistence() {
    given(locks.acquire(anyString())).willReturn(Mono.empty());
    given(identities.upsertIfNewer(any(), any())).willReturn(Mono.just(UUID.randomUUID()));
    given(accountProfiles.upsert(anyString(), anyString(), any(), any()))
        .willReturn(Mono.just(UUID.randomUUID()));
    given(consolidations.deleteFor(any())).willReturn(Mono.empty());
    given(accountLinks.deleteFor(any())).willReturn(Mono.empty());
    given(contacts.deleteFor(any())).willReturn(Mono.empty());
    given(individuals.deleteFor(any())).willReturn(Mono.empty());
    given(legalEntities.deleteFor(any())).willReturn(Mono.empty());
    given(individuals.insert(any(), any())).willReturn(Mono.empty());
    given(legalEntities.insert(any(), any())).willReturn(Mono.empty());
    given(contacts.insert(anyString(), any(), any())).willReturn(Mono.empty());
    given(accountLinks.insert(anyString(), any(), any())).willReturn(Mono.empty());
    given(consolidations.insert(anyString(), any(), any())).willReturn(Mono.empty());
  }
}
