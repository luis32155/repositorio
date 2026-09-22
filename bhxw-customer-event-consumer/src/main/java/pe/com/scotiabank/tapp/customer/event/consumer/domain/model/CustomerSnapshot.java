package pe.com.scotiabank.tapp.customer.event.consumer.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** Validated full snapshot; dynamic fields remain in the original JSON trees. */
public record CustomerSnapshot(
    CustomerEventEnvelope event,
    String sourceId,
    String enterpriseId,
    String userId,
    Instant timestamp) {

  public JsonNode user() {
    return event.payload().path("user_details");
  }

  public JsonNode enterprise() {
    return user().path("enterprise_details");
  }

  public JsonNode triggeringUser() {
    return event.payload().path("triggering_user_details");
  }

  public JsonNode triggeringEnterprise() {
    return triggeringUser().path("enterprise_details");
  }

  public String triggeringEnterpriseId() {
    return triggeringEnterprise().path("enterprise_sco_id").asText();
  }

  public String triggeringUserId() {
    return triggeringUser().path("user_id").asText();
  }

  public JsonNode services() {
    return user().path("entitlements").path("services");
  }

  public String eventName() {
    return event.metadata().path("event_id").asText();
  }

  public BigDecimal orderValue() {
    // PostgreSQL timestamps have microsecond precision; preserve nanosecond ordering separately.
    return BigDecimal.valueOf(timestamp.getEpochSecond())
        .movePointRight(9)
        .add(BigDecimal.valueOf(timestamp.getNano()));
  }
}
