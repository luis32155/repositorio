package pe.com.scotiabank.tapp.service.registration.api.adapters.kafka;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.scotiabank.tapp.service.registration.api.application.ports.input.ProjectTransactionUseCase;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.InvalidProjectionEventException;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.ProjectionPersistenceException;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionResult;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionEvent;
import pe.com.scotiabank.tapp.service.registration.api.fixtures.TransactionProjectionEventFixture;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({
  "PMD.UnitTestContainsTooManyAsserts",
  "PMD.UnitTestAssertionsShouldIncludeMessage"
})
class TransactionProjectionEventConsumerTest {

  private static final String TOPIC = "tapp.transaction.projection.v1";

  @Mock private ProjectTransactionUseCase useCase;

  private TransactionProjectionEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new TransactionProjectionEventConsumer(useCase);
  }

  @Test
  void shouldConsumeEventWithExpectedKafkaKey() {
    var event = TransactionProjectionEventFixture.completedEvent();
    var result = ProjectionResult.applied(event);
    var record = record(event.payload().documentId(), event);
    given(useCase.project(event)).willReturn(Mono.just(result));

    consumer.consume(record);

    verify(useCase).project(event);
  }

  @Test
  void shouldRejectMessageWhenKafkaKeyDoesNotMatchTransactionIdentity() {
    var event = TransactionProjectionEventFixture.completedEvent();
    var record = record("WRONG-KEY", event);

    assertThrows(InvalidProjectionEventException.class, () -> consumer.consume(record));

    verify(useCase, never()).project(event);
  }

  @Test
  void shouldRejectNullKafkaKey() {
    var event = TransactionProjectionEventFixture.completedEvent();
    var record = record(null, event);

    assertThrows(InvalidProjectionEventException.class, () -> consumer.consume(record));

    verify(useCase, never()).project(event);
  }

  @Test
  void shouldRejectNullKafkaValue() {
    var record = record("TXN-001|ABC-001", null);

    assertThrows(InvalidProjectionEventException.class, () -> consumer.consume(record));

    verify(useCase, never()).project(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldRejectEventWithoutPayload() {
    var event = new TransactionProjectionEvent(null, null);
    var record = record("TXN-001|ABC-001", event);

    assertThrows(InvalidProjectionEventException.class, () -> consumer.consume(record));

    verify(useCase, never()).project(event);
  }

  @Test
  void shouldFailWhenProjectionCompletesWithoutResult() {
    var event = TransactionProjectionEventFixture.completedEvent();
    var record = record(event.payload().documentId(), event);
    given(useCase.project(event)).willReturn(Mono.empty());

    assertThrows(ProjectionPersistenceException.class, () -> consumer.consume(record));

    verify(useCase).project(event);
  }

  @Test
  void shouldPropagateProjectionPersistenceErrorForKafkaRetry() {
    var event = TransactionProjectionEventFixture.completedEvent();
    var record = record(event.payload().documentId(), event);
    var expectedError = new ProjectionPersistenceException("PostgreSQL temporalmente caído");
    given(useCase.project(event)).willReturn(Mono.error(expectedError));

    ProjectionPersistenceException error =
        assertThrows(ProjectionPersistenceException.class, () -> consumer.consume(record));

    org.junit.jupiter.api.Assertions.assertSame(expectedError, error);
  }

  @Test
  void shouldHandleDlqEventWithoutThrowing() {
    var event = TransactionProjectionEventFixture.completedEvent();

    assertDoesNotThrow(() -> consumer.handleDlt(event));
  }

  private ConsumerRecord<String, TransactionProjectionEvent> record(
      String key, TransactionProjectionEvent event) {
    return new ConsumerRecord<>(TOPIC, 0, 10L, key, event);
  }
}
