package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerAccountConsolidationTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerAccountLinkTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerAccountProfileTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerDigitalContactTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerIdentityTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerIndividualTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.CustomerLegalEntityTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.application.ports.output.CustomerCoreRepositoryPort;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.CustomerPersistenceException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.InvalidCustomerEventException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionResult;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

/** Replaces one full customer snapshot across seven tables in a single reactive transaction. */
@Repository
@RequiredArgsConstructor
public class PostgresCustomerCoreRepositoryAdapter implements CustomerCoreRepositoryPort {
  private final TransactionalOperator transactions;
  private final Clock clock;
  private final PostgresAdvisoryLockRepository locks;
  private final CustomerIdentityTableRepository identities;
  private final CustomerIndividualTableRepository individuals;
  private final CustomerLegalEntityTableRepository legalEntities;
  private final CustomerDigitalContactTableRepository contacts;
  private final CustomerAccountProfileTableRepository accountProfiles;
  private final CustomerAccountLinkTableRepository accountLinks;
  private final CustomerAccountConsolidationTableRepository consolidations;

  @Override
  public Mono<CustomerProjectionResult> replace(CustomerSnapshot snapshot) {
    return Mono.defer(() -> persist(snapshot, Instant.now(clock)))
        .as(transactions::transactional)
        .onErrorMap(
            error ->
                error instanceof InvalidCustomerEventException
                        || error instanceof CustomerPersistenceException
                    ? error
                    : new CustomerPersistenceException(
                        "Unable to replicate the seven-table customer snapshot", error));
  }

  private Mono<CustomerProjectionResult> persist(CustomerSnapshot snapshot, Instant now) {
    return locks
        .acquire(snapshot.sourceId() + '|' + snapshot.customerKey())
        .then(identities.upsertIfNewer(snapshot, now))
        .flatMap(
            ignored ->
                replaceChildren(snapshot, now)
                    .thenReturn(
                        CustomerProjectionResult.applied(
                            snapshot.customerKey(), snapshot.eventId(), now)))
        .switchIfEmpty(
            Mono.just(
                CustomerProjectionResult.ignored(snapshot.customerKey(), snapshot.eventId(), now)));
  }

  private Mono<Void> replaceChildren(CustomerSnapshot snapshot, Instant now) {
    return saveAccountProfiles(snapshot, now)
        .then(consolidations.deleteFor(snapshot))
        .then(accountLinks.deleteFor(snapshot))
        .then(contacts.deleteFor(snapshot))
        .then(individuals.deleteFor(snapshot))
        .then(legalEntities.deleteFor(snapshot))
        .then(savePersonProfiles(snapshot, now))
        .then(saveContacts(snapshot, now))
        .then(saveAccountLinks(snapshot, now))
        .then(saveConsolidations(snapshot, now));
  }

  private Mono<Void> savePersonProfiles(CustomerSnapshot snapshot, Instant now) {
    Mono<Void> individual =
        snapshot.individual().isObject() ? individuals.insert(snapshot, now) : Mono.empty();
    Mono<Void> legal =
        snapshot.legalEntity().isObject() ? legalEntities.insert(snapshot, now) : Mono.empty();
    return individual.then(legal);
  }

  private Mono<Void> saveAccountProfiles(CustomerSnapshot snapshot, Instant now) {
    return each(snapshot.accountProfiles())
        .concatMap(
            account ->
                accountProfiles.upsert(snapshot.sourceId(), snapshot.eventId(), account, now))
        .then();
  }

  private Mono<Void> saveContacts(CustomerSnapshot snapshot, Instant now) {
    return each(snapshot.digitalContacts())
        .concatMap(contact -> contacts.insert(snapshot.sourceId(), contact, now))
        .then();
  }

  private Mono<Void> saveAccountLinks(CustomerSnapshot snapshot, Instant now) {
    return each(snapshot.accountLinks())
        .concatMap(link -> accountLinks.insert(snapshot.sourceId(), link, now))
        .then();
  }

  private Mono<Void> saveConsolidations(CustomerSnapshot snapshot, Instant now) {
    return each(snapshot.accountConsolidations())
        .concatMap(consolidation -> consolidations.insert(snapshot.sourceId(), consolidation, now))
        .then();
  }

  private Flux<JsonNode> each(JsonNode array) {
    return Flux.range(0, array.size()).map(array::get);
  }
}
