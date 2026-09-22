package pe.com.scotiabank.tapp.service.registration.api.application.ports.input;

import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionResult;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionEvent;
import reactor.core.publisher.Mono;

public interface ProjectTransactionUseCase {

  Mono<ProjectionResult> project(TransactionProjectionEvent event);
}
