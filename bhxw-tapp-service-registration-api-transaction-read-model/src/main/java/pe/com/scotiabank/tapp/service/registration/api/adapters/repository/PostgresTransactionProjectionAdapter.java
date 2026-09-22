package pe.com.scotiabank.tapp.service.registration.api.adapters.repository;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.model.ExistingProjection;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.table.TransactionProjectionTableRepository;
import pe.com.scotiabank.tapp.service.registration.api.application.ports.output.TransactionProjectionRepositoryPort;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.ProjectionPersistenceException;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.VersionConflictException;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionResult;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionEvent;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** Applies versioned transaction projections with one atomic PostgreSQL upsert. */
@Repository
@RequiredArgsConstructor
public class PostgresTransactionProjectionAdapter implements TransactionProjectionRepositoryPort {

  private final TransactionProjectionTableRepository projections;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  @Override
  public Mono<ProjectionResult> upsert(TransactionProjectionEvent event, Instant expiresAt) {
    var payload = event.payload();
    var documentId = payload.documentId();
    return projections
        .upsertIfNewer(
            documentId,
            event.metadata().eventId(),
            jsonMapper.writeValueAsString(payload),
            clock.instant(),
            expiresAt)
        .map(ignored -> ProjectionResult.applied(event))
        .switchIfEmpty(Mono.defer(() -> classifyNonApplied(documentId, event)))
        .onErrorMap(
            DataAccessException.class,
            error ->
                new ProjectionPersistenceException(
                    "No se pudo persistir la proyección en PostgreSQL", error));
  }

  private Mono<ProjectionResult> classifyNonApplied(
      String documentId, TransactionProjectionEvent event) {
    return projections
        .findState(documentId)
        .switchIfEmpty(
            Mono.error(
                new ProjectionPersistenceException(
                    "No se encontró la proyección después del conflicto")))
        .flatMap(current -> classify(current, event));
  }

  private Mono<ProjectionResult> classify(
      ExistingProjection current, TransactionProjectionEvent event) {
    long incomingVersion = event.payload().aggregateVersion();
    if (current.lastEventId().equals(event.metadata().eventId())) {
      return Mono.just(ProjectionResult.duplicate(event, current.aggregateVersion()));
    }
    if (current.aggregateVersion() > incomingVersion) {
      return Mono.just(ProjectionResult.outdated(event, current.aggregateVersion()));
    }
    if (current.aggregateVersion() == incomingVersion) {
      return Mono.error(
          new VersionConflictException(
              "La versión " + incomingVersion + " ya fue aplicada con otro event_id"));
    }
    return Mono.error(
        new ProjectionPersistenceException(
            "Conflicto concurrente no clasificable; se reintentará"));
  }
}
