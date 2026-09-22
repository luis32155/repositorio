package pe.com.scotiabank.tapp.customer.event.consumer.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.com.scotiabank.tapp.customer.event.consumer.application.mapping.CustomerSnapshotMapper;
import pe.com.scotiabank.tapp.customer.event.consumer.application.ports.input.ProcessCustomerEventUseCase;
import pe.com.scotiabank.tapp.customer.event.consumer.application.ports.output.CustomerCoreRepositoryPort;
import pe.com.scotiabank.tapp.customer.event.consumer.application.validation.CustomerEventValidator;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionResult;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CustomerEventProjectionService implements ProcessCustomerEventUseCase {
  private final CustomerEventValidator validator;
  private final CustomerSnapshotMapper mapper;
  private final CustomerCoreRepositoryPort repository;

  @Override
  public Mono<CustomerProjectionResult> process(CustomerEventEnvelope event) {
    return Mono.fromCallable(() -> validator.validate(event))
        .map(mapper::map)
        .flatMap(repository::replace);
  }
}
