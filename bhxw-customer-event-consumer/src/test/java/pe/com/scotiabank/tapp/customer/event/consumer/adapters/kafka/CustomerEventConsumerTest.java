package pe.com.scotiabank.tapp.customer.event.consumer.adapters.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;
import pe.com.scotiabank.tapp.customer.event.consumer.application.ports.input.ProcessCustomerEventUseCase;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.CustomerPersistenceException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.InvalidCustomerEventException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionResult;
import pe.com.scotiabank.tapp.customer.event.consumer.fixtures.CustomerEventFixture;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
// Verify completion and side effects together for each delivery outcome.
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class CustomerEventConsumerTest {
  @Mock private ProcessCustomerEventUseCase useCase;
  private CustomerEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new CustomerEventConsumer(useCase);
  }

  @Test
  void shouldWaitForPersistenceBeforeCompletingWithoutBlockingCaller() {
    var event = CustomerEventFixture.event();
    var sink = Sinks.<CustomerProjectionResult>one();
    given(useCase.process(event)).willReturn(sink.asMono());
    var completion = consumer.consume(record("40723|55421", event));
    verifyNoInteractions(useCase);
    StepVerifier.create(completion)
        .expectSubscription()
        .expectNoEvent(Duration.ofMillis(30))
        .then(() -> sink.tryEmitValue(CustomerEventFixture.projectionResult()))
        .verifyComplete();
    verify(useCase).process(event);
  }

  @Test
  void shouldAcceptProducerKeyWithoutInventingAnAggregateId() {
    var event = CustomerEventFixture.event();
    given(useCase.process(event)).willReturn(Mono.just(CustomerEventFixture.projectionResult()));
    StepVerifier.create(consumer.consume(record("producer-defined-key", event))).verifyComplete();
    verify(useCase).process(event);
  }

  @Test
  void shouldRejectTombstoneAsReactiveError() {
    StepVerifier.create(consumer.consume(record("CUST-001", null)))
        .expectError(InvalidCustomerEventException.class)
        .verify();
  }

  @Test
  void shouldRetryPersistenceFailureAndPropagateExhaustion() {
    var event = CustomerEventFixture.event();
    given(useCase.process(event))
        .willReturn(Mono.error(new CustomerPersistenceException("offline")));
    StepVerifier.withVirtualTime(() -> consumer.consume(record("40723|55421", event)))
        .thenAwait(Duration.ofMinutes(1))
        .expectError(CustomerPersistenceException.class)
        .verify();
    verify(useCase, times(4)).process(event);
  }

  @Test
  void shouldPropagateInvalidPayloadWithoutRetry() {
    var event = CustomerEventFixture.event();
    given(useCase.process(event))
        .willReturn(Mono.error(new InvalidCustomerEventException("invalid")));
    StepVerifier.create(consumer.consume(record("40723|55421", event)))
        .expectError(InvalidCustomerEventException.class)
        .verify();
    verify(useCase).process(event);
  }

  @Test
  void shouldSubscribeToOneTopic() throws NoSuchMethodException {
    var method = CustomerEventConsumer.class.getMethod("consume", ConsumerRecord.class);
    assertThat(method.getAnnotation(KafkaListener.class).topics())
        .containsExactly("${app.kafka.topic}");
  }

  private ConsumerRecord<String, CustomerEventEnvelope> record(
      String key, CustomerEventEnvelope event) {
    return new ConsumerRecord<>("bhxw.tapp.customer.cdc.v1", 0, 10L, key, event);
  }
}
