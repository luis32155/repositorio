package pe.com.scotiabank.tapp.customer.event.consumer.application.mapping;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Component;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;

/** Pure mapping from the dynamic event envelope to the persistence snapshot. */
@Component
public class CustomerSnapshotMapper {
  public CustomerSnapshot map(CustomerEventEnvelope event) {
    var identity = event.payload().path("customer_identity");
    var metadata = event.metadata();
    var timestamp = Instant.parse(metadata.path("event_timestamp").asText());
    var sequence =
        metadata.hasNonNull("source_sequence")
            ? new BigDecimal(metadata.path("source_sequence").asText())
            : timestampSequence(timestamp);
    return new CustomerSnapshot(
        event,
        metadata.path("source_id").asText(),
        identity.path("country_code").asText(),
        identity.path("document_type").asText(),
        identity.path("document_number").asText(),
        metadata.path("event_id").asText(),
        timestamp,
        sequence);
  }

  private BigDecimal timestampSequence(Instant timestamp) {
    return BigDecimal.valueOf(timestamp.getEpochSecond())
        .movePointRight(9)
        .add(BigDecimal.valueOf(timestamp.getNano()));
  }
}
