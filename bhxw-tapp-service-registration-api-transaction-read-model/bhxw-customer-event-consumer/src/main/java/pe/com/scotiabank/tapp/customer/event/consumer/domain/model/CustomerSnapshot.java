package pe.com.scotiabank.tapp.customer.event.consumer.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** Validated full CORE-equivalent customer snapshot. Unknown fields remain in raw JSONB. */
public record CustomerSnapshot(
    CustomerEventEnvelope event,
    String sourceId,
    String countryCode,
    String documentType,
    String documentNumber,
    String eventId,
    Instant eventTimestamp,
    BigDecimal sourceSequence) {

  public JsonNode payload() {
    return event.payload();
  }

  public JsonNode metadata() {
    return event.metadata();
  }

  public JsonNode identity() {
    return payload().path("customer_identity");
  }

  public JsonNode individual() {
    return payload().path("individual_profile");
  }

  public JsonNode legalEntity() {
    return payload().path("legal_entity_profile");
  }

  public JsonNode digitalContacts() {
    return payload().path("digital_contacts");
  }

  public JsonNode accountProfiles() {
    return payload().path("account_profiles");
  }

  public JsonNode accountLinks() {
    return payload().path("customer_account_links");
  }

  public JsonNode accountConsolidations() {
    return payload().path("account_consolidations");
  }

  public String customerKey() {
    return countryCode + '|' + documentType + '|' + documentNumber;
  }
}
