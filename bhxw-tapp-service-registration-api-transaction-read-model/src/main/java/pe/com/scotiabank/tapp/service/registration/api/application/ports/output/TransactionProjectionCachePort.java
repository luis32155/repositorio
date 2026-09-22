package pe.com.scotiabank.tapp.service.registration.api.application.ports.output;

import java.time.Instant;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionEvent;
import reactor.core.publisher.Mono;

public interface TransactionProjectionCachePort {

  Mono<Void> put(TransactionProjectionEvent event, Instant expiresAt);
}
