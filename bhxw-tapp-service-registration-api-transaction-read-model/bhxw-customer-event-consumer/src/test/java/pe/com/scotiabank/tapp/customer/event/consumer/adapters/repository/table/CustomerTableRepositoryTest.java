package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerAccountConsolidationEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerAccountLinkEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerAccountProfileEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerDigitalContactEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerIdentityEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerIndividualEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerLegalEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.fixtures.CustomerEventFixture;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class CustomerTableRepositoryTest {
  private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

  @Test
  void shouldExposeTechnicalKeysForAllReactiveCrudEntities() {
    var key = UUID.randomUUID();
    assertThat(new CustomerIdentityEntity(key).customerIdentityKey()).isEqualTo(key);
    assertThat(new CustomerIndividualEntity(key).customerIndividualKey()).isEqualTo(key);
    assertThat(new CustomerLegalEntity(key).customerLegalEntityKey()).isEqualTo(key);
    assertThat(new CustomerDigitalContactEntity(key).customerDigitalContactKey()).isEqualTo(key);
    assertThat(new CustomerAccountProfileEntity(key).customerAccountProfileKey()).isEqualTo(key);
    assertThat(new CustomerAccountLinkEntity(key).customerAccountLinkKey()).isEqualTo(key);
    assertThat(new CustomerAccountConsolidationEntity(key).customerAccountConsolidationKey())
        .isEqualTo(key);
  }

  @Test
  void shouldAdaptIdentityAndAllProfileOperationsToSqlMethods() {
    var snapshot = CustomerEventFixture.snapshot(CustomerEventFixture.event());
    var identity = mock(CustomerIdentityTableRepository.class, CALLS_REAL_METHODS);
    var individual = mock(CustomerIndividualTableRepository.class, CALLS_REAL_METHODS);
    var legal = mock(CustomerLegalEntityTableRepository.class, CALLS_REAL_METHODS);
    given(
            identity.upsertRow(
                anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
        .willReturn(Mono.just(UUID.randomUUID()));
    given(individual.deleteRows(anyString(), anyString(), anyString(), anyString()))
        .willReturn(Mono.just(1));
    given(individual.insertRow(anyString(), anyString(), any()))
        .willReturn(Mono.just(UUID.randomUUID()));
    given(legal.deleteRows(anyString(), anyString(), anyString(), anyString()))
        .willReturn(Mono.just(1));
    given(legal.insertRow(anyString(), anyString(), any()))
        .willReturn(Mono.just(UUID.randomUUID()));

    StepVerifier.create(
            identity
                .upsertIfNewer(snapshot, NOW)
                .then(individual.deleteFor(snapshot))
                .then(individual.insert(snapshot, NOW))
                .then(legal.deleteFor(snapshot))
                .then(legal.insert(snapshot, NOW)))
        .verifyComplete();

    verify(identity)
        .upsertRow(
            snapshot.sourceId(),
            snapshot.identity().toString(),
            snapshot.eventId(),
            snapshot.sourceSequence(),
            snapshot.payload().toString(),
            snapshot.metadata().toString(),
            NOW);
    verify(individual).insertRow(snapshot.sourceId(), snapshot.individual().toString(), NOW);
    assertThat(snapshot.customerKey()).isEqualTo("604|01|12345678");
  }

  @Test
  void shouldAdaptContactAccountLinkAndConsolidationOperations() {
    var snapshot = CustomerEventFixture.snapshot(CustomerEventFixture.event());
    var contact = mock(CustomerDigitalContactTableRepository.class, CALLS_REAL_METHODS);
    var account = mock(CustomerAccountProfileTableRepository.class, CALLS_REAL_METHODS);
    var link = mock(CustomerAccountLinkTableRepository.class, CALLS_REAL_METHODS);
    var consolidation = mock(CustomerAccountConsolidationTableRepository.class, CALLS_REAL_METHODS);
    given(contact.deleteRows(anyString(), anyString(), anyString(), anyString()))
        .willReturn(Mono.just(1));
    given(contact.insertRow(anyString(), anyString(), any()))
        .willReturn(Mono.just(UUID.randomUUID()));
    given(account.upsertRow(anyString(), anyString(), anyString(), any()))
        .willReturn(Mono.just(UUID.randomUUID()));
    given(link.deleteRows(anyString(), anyString(), anyString(), anyString()))
        .willReturn(Mono.just(1));
    given(link.insertRow(anyString(), anyString(), any())).willReturn(Mono.just(UUID.randomUUID()));
    given(consolidation.deleteRows(anyString(), anyString(), anyString(), anyString()))
        .willReturn(Mono.just(1));
    given(consolidation.insertRow(anyString(), anyString(), any()))
        .willReturn(Mono.just(UUID.randomUUID()));

    StepVerifier.create(
            contact
                .deleteFor(snapshot)
                .then(contact.insert(snapshot.sourceId(), snapshot.digitalContacts().get(0), NOW))
                .then(
                    account.upsert(
                        snapshot.sourceId(),
                        snapshot.eventId(),
                        snapshot.accountProfiles().get(0),
                        NOW))
                .then(link.deleteFor(snapshot))
                .then(link.insert(snapshot.sourceId(), snapshot.accountLinks().get(0), NOW))
                .then(consolidation.deleteFor(snapshot))
                .then(
                    consolidation.insert(
                        snapshot.sourceId(), snapshot.accountConsolidations().get(0), NOW)))
        .verifyComplete();

    verify(account)
        .upsertRow(
            snapshot.sourceId(),
            snapshot.eventId(),
            snapshot.accountProfiles().get(0).toString(),
            NOW);
  }
}
