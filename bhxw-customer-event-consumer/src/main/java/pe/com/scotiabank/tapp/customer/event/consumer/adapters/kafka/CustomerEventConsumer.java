package pe.com.scotiabank.tapp.customer.event.consumer.adapters.kafka;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pe.com.scotiabank.tapp.customer.event.consumer.application.ports.input.ProcessCustomerEventUseCase;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.CustomerPersistenceException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.InvalidCustomerEventException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionResult;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerProjectionStatus;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerEventConsumer {
  private final ProcessCustomerEventUseCase useCase;

  @KafkaListener(
      id = "customerEvents",
      topics = "${app.kafka.topic}",
      errorHandler = "customerListenerErrorHandler",
      groupId = "${app.kafka.consumer-group}")
  public Mono<Void> consume(ConsumerRecord<String, CustomerEventEnvelope> record) {
    return Mono.defer(
        () -> {
          long started = System.nanoTime();
          var status = new AtomicReference<CustomerProjectionStatus>();
          return processRecord(record)
              .retryWhen(
                  Retry.backoff(3, Duration.ofSeconds(1))
                      .filter(CustomerPersistenceException.class::isInstance)
                      .doBeforeRetry(signal -> logRetry(record, signal.totalRetries() + 1))
                      .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
              .doOnNext(result -> status.set(result.status()))
              .then()
              // Observe completion, not onNext: successful logs must follow transaction commit.
              .doOnSuccess(ignored -> logCompleted(record, status.get(), started))
              .doOnError(error -> logFailed(record, error, started));
        });
  }

  private Mono<CustomerProjectionResult> processRecord(
      ConsumerRecord<String, CustomerEventEnvelope> record) {
    return Mono.defer(
        () -> {
          var event = record.value();
          if (event == null) {
            return Mono.error(
                new InvalidCustomerEventException(
                    "Kafka tombstones are not full customer snapshots"));
          }
          return useCase
              .process(event)
              .switchIfEmpty(
                  Mono.error(
                      new CustomerPersistenceException(
                          "Customer projection finished without result")));
        });
  }

  private void logCompleted(
      ConsumerRecord<String, CustomerEventEnvelope> record,
      CustomerProjectionStatus status,
      long started) {
    if (log.isInfoEnabled()) {
      var metadata = record.value().metadata();
      log.info(
          "Customer event completed event_id={} correlation_id={} trace_id={} topic={} partition={} offset={} status={} elapsed_ms={}",
          metadata.path("event_id").asText(),
          metadata.path("correlation_id").asText(),
          metadata.path("trace_id").asText(),
          record.topic(),
          record.partition(),
          record.offset(),
          status,
          elapsedMillis(started));
    }
  }

  private void logRetry(ConsumerRecord<String, CustomerEventEnvelope> record, long attempt) {
    if (log.isWarnEnabled()) {
      log.warn(
          "Customer persistence retry topic={} partition={} offset={} retry={}",
          record.topic(),
          record.partition(),
          record.offset(),
          attempt);
    }
  }

  private void logFailed(
      ConsumerRecord<String, CustomerEventEnvelope> record, Throwable error, long started) {
    if (log.isErrorEnabled()) {
      // Exception messages and payloads can contain personal data or SQL parameter values.
      log.error(
          "Customer event failed topic={} partition={} offset={} error_type={} elapsed_ms={}",
          record.topic(),
          record.partition(),
          record.offset(),
          error.getClass().getSimpleName(),
          elapsedMillis(started));
    }
  }

  private long elapsedMillis(long started) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
  }
}
