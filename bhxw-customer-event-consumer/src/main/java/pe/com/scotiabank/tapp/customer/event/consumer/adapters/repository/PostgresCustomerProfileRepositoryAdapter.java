package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.model.ExistingCustomerSnapshot;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.AccountTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.EnterpriseTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.EnterpriseUserTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.ServiceTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.UserServiceAccountTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.UserServiceTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table.UserTableRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.application.ports.output.CustomerProfileRepositoryPort;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.CustomerPersistenceException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.InvalidCustomerEventException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionResult;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

/** Coordinates the seven table gateways inside one non-blocking PostgreSQL transaction. */
@Repository
@RequiredArgsConstructor
public class PostgresCustomerProfileRepositoryAdapter implements CustomerProfileRepositoryPort {
  private final TransactionalOperator transactions;
  private final Clock clock;
  private final PostgresAdvisoryLockRepository locks;
  private final EnterpriseTableRepository enterprises;
  private final UserTableRepository users;
  private final EnterpriseUserTableRepository enterpriseUsers;
  private final ServiceTableRepository services;
  private final AccountTableRepository accounts;
  private final UserServiceTableRepository userServices;
  private final UserServiceAccountTableRepository userServiceAccounts;

  @Override
  public Mono<CustomerProjectionResult> upsert(CustomerSnapshot snapshot) {
    return Mono.defer(() -> persist(snapshot, Instant.now(clock)))
        .as(transactions::transactional)
        .onErrorMap(
            error ->
                error instanceof InvalidCustomerEventException
                        || error instanceof CustomerPersistenceException
                    ? error
                    : new CustomerPersistenceException(
                        "Unable to replicate customer snapshot in PostgreSQL", error));
  }

  private Mono<CustomerProjectionResult> persist(CustomerSnapshot snapshot, Instant now) {
    return acquireAggregateLocks(snapshot)
        .then(
            enterpriseUsers
                .findSnapshot(snapshot)
                .flatMap(existing -> shouldApply(snapshot, existing))
                .defaultIfEmpty(true))
        .flatMap(
            apply ->
                apply
                    ? applySnapshot(snapshot, now)
                    : Mono.just(
                        CustomerProjectionResult.ignored(
                            snapshot.userId(), snapshot.eventName(), now)));
  }

  private Mono<Boolean> shouldApply(CustomerSnapshot snapshot, ExistingCustomerSnapshot existing) {
    if (existing.ordering() == null) {
      return Mono.just(true);
    }
    int comparison = existing.ordering().compareTo(snapshot.orderValue());
    if (comparison == 0 && !existing.samePayload()) {
      return Mono.error(
          new InvalidCustomerEventException(
              "Conflicting full snapshots have the same timestamp; producer revision is required"));
    }
    return Mono.just(comparison < 0);
  }

  private Mono<CustomerProjectionResult> applySnapshot(CustomerSnapshot snapshot, Instant now) {
    return saveEnterprises(snapshot, now)
        .then(saveActor(snapshot, now))
        .flatMap(actorKey -> saveTarget(snapshot, actorKey, now))
        .flatMap(targetKey -> replaceEntitlements(snapshot, targetKey, now))
        .thenReturn(CustomerProjectionResult.applied(snapshot.userId(), snapshot.eventName(), now));
  }

  private Mono<Void> saveEnterprises(CustomerSnapshot snapshot, Instant now) {
    return enterprises
        .upsert(
            snapshot.sourceId(),
            snapshot.triggeringEnterpriseId(),
            snapshot.triggeringEnterprise(),
            now)
        .then(
            enterprises.upsert(
                snapshot.sourceId(), snapshot.enterpriseId(), snapshot.enterprise(), now));
  }

  private Mono<UUID> saveActor(CustomerSnapshot snapshot, Instant now) {
    return saveUser(
        snapshot.sourceId(),
        snapshot.triggeringEnterpriseId(),
        snapshot.triggeringUserId(),
        snapshot.triggeringUser(),
        now,
        true,
        userKey ->
            enterpriseUsers.saveActor(
                snapshot.sourceId(),
                snapshot.triggeringEnterpriseId(),
                snapshot.triggeringUserId(),
                userKey,
                snapshot.triggeringUser(),
                now));
  }

  private Mono<UUID> saveTarget(CustomerSnapshot snapshot, UUID actorKey, Instant now) {
    return saveUser(
        snapshot.sourceId(),
        snapshot.enterpriseId(),
        snapshot.userId(),
        snapshot.user(),
        now,
        false,
        userKey -> enterpriseUsers.saveTarget(snapshot, userKey, actorKey, now));
  }

  private Mono<UUID> saveUser(
      String sourceId,
      String enterpriseId,
      String externalUserId,
      JsonNode user,
      Instant now,
      boolean partial,
      Function<UUID, Mono<UUID>> saveMembership) {
    return enterpriseUsers
        .findIdentity(sourceId, enterpriseId, externalUserId)
        .flatMap(
            identity ->
                updateUser(identity.userKey(), user, now, partial)
                    .then(saveMembership.apply(identity.userKey())))
        .switchIfEmpty(users.insert(user, now).flatMap(saveMembership));
  }

  private Mono<Void> updateUser(UUID userKey, JsonNode user, Instant now, boolean partial) {
    return partial ? users.patch(userKey, user, now) : users.update(userKey, user, now);
  }

  private Mono<Void> replaceEntitlements(
      CustomerSnapshot snapshot, UUID enterpriseUserKey, Instant now) {
    return userServices
        .deleteAll(enterpriseUserKey)
        .thenMany(
            Flux.range(0, snapshot.services().size())
                .concatMap(
                    serviceOrder ->
                        saveService(
                            snapshot,
                            enterpriseUserKey,
                            snapshot.services().get(serviceOrder),
                            serviceOrder,
                            now)))
        .then();
  }

  private Mono<Void> saveService(
      CustomerSnapshot snapshot,
      UUID enterpriseUserKey,
      JsonNode service,
      int serviceOrder,
      Instant now) {
    return services
        .upsert(snapshot.sourceId(), snapshot.enterpriseId(), service, now)
        .flatMap(
            serviceKey ->
                userServices.insert(
                    snapshot.sourceId(),
                    snapshot.enterpriseId(),
                    enterpriseUserKey,
                    serviceKey,
                    service,
                    serviceOrder,
                    now))
        .flatMap(
            userServiceKey ->
                Flux.range(0, service.path("accounts").size())
                    .concatMap(
                        accountOrder ->
                            saveAccount(
                                snapshot,
                                userServiceKey,
                                service.path("accounts").get(accountOrder),
                                accountOrder,
                                now))
                    .then());
  }

  private Mono<Void> saveAccount(
      CustomerSnapshot snapshot,
      UUID userServiceKey,
      JsonNode account,
      int accountOrder,
      Instant now) {
    return accounts
        .upsert(snapshot.sourceId(), snapshot.enterpriseId(), account, now)
        .flatMap(
            accountKey ->
                userServiceAccounts.insert(
                    snapshot.sourceId(),
                    snapshot.enterpriseId(),
                    userServiceKey,
                    accountKey,
                    account,
                    accountOrder,
                    now));
  }

  private Mono<Void> acquireAggregateLocks(CustomerSnapshot snapshot) {
    var target = identity(snapshot.sourceId(), snapshot.enterpriseId(), snapshot.userId());
    var actor =
        identity(
            snapshot.sourceId(), snapshot.triggeringEnterpriseId(), snapshot.triggeringUserId());
    var identities = List.of(target, actor).stream().distinct().sorted().toList();
    return Flux.fromIterable(identities).concatMap(locks::acquire).then();
  }

  private String identity(String sourceId, String enterpriseId, String userId) {
    return sourceId + '|' + enterpriseId + '|' + userId;
  }
}
