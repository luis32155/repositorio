package pe.com.scotiabank.tapp.customer.event.consumer.application.mapping;

import java.time.Instant;
import org.springframework.stereotype.Component;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;

/** Pure mapping from a validated event to the domain snapshot used by the output port. */
@Component
public class CustomerSnapshotMapper {
  public CustomerSnapshot map(CustomerEventEnvelope event) {
    var user = event.payload().path("user_details");
    var metadataTimestamp = Instant.parse(event.metadata().path("event_timestamp").asText());
    var snapshotTimestamp =
        user.hasNonNull("event_timestamp")
            ? Instant.parse(user.path("event_timestamp").asText())
            : metadataTimestamp;
    return new CustomerSnapshot(
        event,
        event.metadata().path("source_id").asText(),
        user.path("enterprise_details").path("enterprise_sco_id").asText(),
        user.path("user_id").asText(),
        snapshotTimestamp);
  }
}
