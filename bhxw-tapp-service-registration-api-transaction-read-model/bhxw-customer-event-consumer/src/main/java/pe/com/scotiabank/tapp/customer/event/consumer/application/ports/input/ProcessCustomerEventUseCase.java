package pe.com.scotiabank.tapp.customer.event.consumer.application.ports.input;

import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionResult;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ProcessCustomerEventUseCase {
  Mono<CustomerProjectionResult> process(CustomerEventEnvelope event);
}
