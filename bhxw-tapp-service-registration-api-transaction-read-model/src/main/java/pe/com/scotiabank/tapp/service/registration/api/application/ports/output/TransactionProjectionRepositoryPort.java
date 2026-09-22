package pe.com.scotiabank.tapp.service.registration.api.application.ports.output;

import java.time.Instant;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionResult;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionEvent;
import reactor.core.publisher.Mono;

public interface TransactionProjectionRepositoryPort {

  Mono<ProjectionResult> upsert(TransactionProjectionEvent event, Instant expiresAt);
}
