package pe.com.scotiabank.tapp.service.registration.api.adapters.kafka;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.stereotype.Component;
import pe.com.scotiabank.tapp.service.registration.api.application.ports.input.ProjectTransactionUseCase;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.InvalidProjectionEventException;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.ProjectionPersistenceException;
import pe.com.scotiabank.tapp.service.registration.api.domain.exception.VersionConflictException;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.ProjectionResult;
import pe.com.scotiabank.tapp.service.registration.api.domain.model.TransactionProjectionEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionProjectionEventConsumer {

  private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(30);

  private final ProjectTransactionUseCase projectTransactionUseCase;

  @RetryableTopic(
      attempts = "4",
      backOff = @BackOff(delay = 2000),
      retryTopicSuffix = ".retry",
      dltTopicSuffix = ".dlq",
      sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
      autoCreateTopics = "false",
      include = ProjectionPersistenceException.class,
      exclude = {InvalidProjectionEventException.class, VersionConflictException.class})
  @KafkaListener(
      topics = "${app.kafka.topics.transaction-projection}",
      groupId = "${app.kafka.consumer-group}")
  public void consume(ConsumerRecord<String, TransactionProjectionEvent> record) {
    TransactionProjectionEvent event = record.value();
    validateKafkaKey(record.key(), event);

    ProjectionResult result =
        projectTransactionUseCase
            .project(event)
            .blockOptional(PROCESSING_TIMEOUT)
            .orElseThrow(
                () -> new ProjectionPersistenceException("La proyección terminó sin resultado"));

    if (log.isInfoEnabled()) {
      log.info(
          "projection_result={} transaction_id={} req_msg_id={} event_id={} "
              + "incoming_version={} current_version={} topic={} partition={} offset={}",
          result.result(),
          result.transactionId(),
          result.reqMsgId(),
          result.eventId(),
          result.incomingVersion(),
          result.currentVersion(),
          record.topic(),
          record.partition(),
          record.offset());
    }
  }

  @DltHandler
  public void handleDlt(TransactionProjectionEvent event) {
    if (log.isErrorEnabled()) {
      log.error(
          "projection_sent_to_dlq transaction_id={} req_msg_id={} event_id={}",
          event.payload().transactionId(),
          event.payload().reqMsgId(),
          event.metadata().eventId());
    }
  }

  private void validateKafkaKey(String kafkaKey, TransactionProjectionEvent event) {
    if (event == null || event.payload() == null) {
      throw new InvalidProjectionEventException("El mensaje Kafka no contiene payload");
    }
    String expectedKey = event.payload().documentId();
    if (!expectedKey.equals(kafkaKey)) {
      throw new InvalidProjectionEventException("Kafka key inválida. Se esperaba " + expectedKey);
    }
  }
}
