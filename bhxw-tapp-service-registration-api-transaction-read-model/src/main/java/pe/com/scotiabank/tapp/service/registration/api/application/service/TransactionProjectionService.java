package pe.com.scotiabank.tapp.service.registration.api.application.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.com.scotiabank.tapp.service.registration.api.application.ports.input.ProjectTransactionUseCase;
import pe.com.scotiabank.tapp.service.registration.api.application.ports.output.TransactionProjectionCachePort;
import pe.com.scotiabank.tapp.service.registration.api.application.ports.output.TransactionProjectionRepositoryPort;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.InvalidProjectionEventException;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionResult;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionResultType;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionEvent;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionStatus;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TransactionProjectionService implements ProjectTransactionUseCase {

  private final TransactionProjectionRepositoryPort repository;
  private final TransactionProjectionCachePort cache;
  private final Validator validator;

  @Override
  public Mono<ProjectionResult> project(TransactionProjectionEvent event) {
    return Mono.defer(
        () -> {
          validate(event);
          Instant expiration = calculateExpiration(event);
          return repository
              .upsert(event, expiration)
              .flatMap(result -> cacheIfApplied(event, expiration, result));
        });
  }

  private Mono<ProjectionResult> cacheIfApplied(
      TransactionProjectionEvent event, Instant expiration, ProjectionResult result) {
    if (result.result() != ProjectionResultType.APPLIED) {
      return Mono.just(result);
    }
    return cache.put(event, expiration).thenReturn(result);
  }

  private void validate(TransactionProjectionEvent event) {
    if (event == null) {
      throw new InvalidProjectionEventException("El evento de proyección es obligatorio");
    }

    Set<ConstraintViolation<TransactionProjectionEvent>> violations = validator.validate(event);
    if (!violations.isEmpty()) {
      String detail =
          violations.stream()
              .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
              .sorted()
              .collect(Collectors.joining(", "));
      throw new InvalidProjectionEventException("Evento de proyección inválido: " + detail);
    }

    if (event.payload().completedAt() != null
        && event.payload().completedAt().isBefore(event.payload().startedAt())) {
      throw new InvalidProjectionEventException("completed_at no puede ser anterior a started_at");
    }

    if (event.payload().transactionStatus() == TransactionStatus.COMPLETED
        && event.payload().completedAt() == null) {
      throw new InvalidProjectionEventException(
          "completed_at es obligatorio cuando transaction_status es COMPLETED");
    }
  }

  private Instant calculateExpiration(TransactionProjectionEvent event) {
    Instant completedAt = event.payload().completedAt();
    if (completedAt == null) {
      return null;
    }
    return completedAt.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
  }
}
