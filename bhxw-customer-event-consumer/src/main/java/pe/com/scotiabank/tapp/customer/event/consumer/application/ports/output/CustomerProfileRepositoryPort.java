package pe.com.scotiabank.tapp.customer.event.consumer.application.ports.output;

import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionResult;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface CustomerProfileRepositoryPort {

  Mono<CustomerProjectionResult> upsert(CustomerSnapshot snapshot);
}
