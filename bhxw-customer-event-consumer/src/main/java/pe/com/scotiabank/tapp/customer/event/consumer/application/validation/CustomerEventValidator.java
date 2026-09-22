package pe.com.scotiabank.tapp.customer.event.consumer.application.validation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.InvalidCustomerEventException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import tools.jackson.databind.JsonNode;

/** Validates the stable part of the event contract while allowing additional dynamic fields. */
@Component
public class CustomerEventValidator {
  private static final String EVENT_TIMESTAMP = "event_timestamp";

  public CustomerEventEnvelope validate(CustomerEventEnvelope event) {
    if (event == null) {
      throw new InvalidCustomerEventException("Customer event is mandatory");
    }
    object(event.payload(), "payload");
    object(event.metadata(), "metadata");
    var actor = requiredObject(event.payload(), "triggering_user_details");
    var user = requiredObject(event.payload(), "user_details");
    var actorEnterprise = requiredObject(actor, "enterprise_details");
    var enterprise = requiredObject(user, "enterprise_details");
    var entitlements = requiredObject(user, "entitlements");
    var services = requiredArray(entitlements, "services");

    requiredText(event.metadata(), "source_id");
    requiredText(event.metadata(), "event_id");
    timestamp(requiredText(event.metadata(), EVENT_TIMESTAMP), EVENT_TIMESTAMP);
    requiredText(actor, "user_id");
    requiredText(actorEnterprise, "enterprise_sco_id");
    requiredText(user, "user_id");
    requiredText(enterprise, "enterprise_sco_id");
    optionalTimestamp(actor, "last_signin_timestamp");
    optionalTimestamp(user, EVENT_TIMESTAMP);
    optionalInteger(user, "document_type");
    optionalDate(user, "birth_date");
    optionalDate(enterprise, "constitution_date");
    optionalBoolean(entitlements, "recipient_maintenance_enabled");
    optionalObject(entitlements, "account_transfer")
        .ifPresent(transfer -> optionalBoolean(transfer, "enabled"));
    validateServices(services);
    return event;
  }

  private void validateServices(JsonNode services) {
    Set<String> serviceIds = new HashSet<>();
    services.forEach(
        service -> {
          object(service, "service");
          unique(serviceIds, requiredText(service, "service_id"), "service_id");
          optionalBoolean(service, "service_selected");
          optionalObject(service, "service_details").ifPresent(this::limits);
          var accounts = requiredArray(service, "accounts");
          Set<String> accountNumbers = new HashSet<>();
          accounts.forEach(
              account -> {
                object(account, "account");
                unique(accountNumbers, requiredText(account, "account_number"), "account_number");
                limits(account);
              });
        });
  }

  private void limits(JsonNode node) {
    for (String field : new String[] {"transaction_limit", "daily_limit"}) {
      if (node.hasNonNull(field)
          && (!node.get(field).isNumber() || node.get(field).decimalValue().signum() < 0)) {
        throw new InvalidCustomerEventException(field + " must be a non-negative number");
      }
    }
  }

  private void optionalInteger(JsonNode node, String field) {
    if (node.hasNonNull(field)
        && (!node.get(field).isIntegralNumber() || !node.get(field).canConvertToInt())) {
      throw new InvalidCustomerEventException(field + " must be an integer");
    }
  }

  private void optionalBoolean(JsonNode node, String field) {
    if (node.hasNonNull(field) && !node.get(field).isBoolean()) {
      throw new InvalidCustomerEventException(field + " must be a boolean");
    }
  }

  private void optionalDate(JsonNode node, String field) {
    if (node.hasNonNull(field)) {
      try {
        LocalDate.parse(requiredText(node, field));
      } catch (DateTimeParseException exception) {
        throw new InvalidCustomerEventException(
            field + " must be a valid YYYY-MM-DD date", exception);
      }
    }
  }

  private void optionalTimestamp(JsonNode node, String field) {
    if (node.hasNonNull(field)) {
      timestamp(requiredText(node, field), field);
    }
  }

  private void timestamp(String value, String field) {
    try {
      Instant.parse(value);
    } catch (DateTimeParseException exception) {
      throw new InvalidCustomerEventException(field + " must be a valid UTC instant", exception);
    }
  }

  private java.util.Optional<JsonNode> optionalObject(JsonNode parent, String field) {
    if (!parent.hasNonNull(field)) {
      return java.util.Optional.empty();
    }
    var value = parent.get(field);
    object(value, field);
    return java.util.Optional.of(value);
  }

  private JsonNode requiredObject(JsonNode parent, String field) {
    var value = parent.path(field);
    object(value, field);
    return value;
  }

  private JsonNode requiredArray(JsonNode parent, String field) {
    var value = parent.path(field);
    if (!value.isArray()) {
      throw new InvalidCustomerEventException(field + " must be an array in a full snapshot");
    }
    return value;
  }

  private String requiredText(JsonNode node, String field) {
    if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) {
      throw new InvalidCustomerEventException(field + " must be a non-empty string");
    }
    return node.path(field).asText();
  }

  private void unique(Set<String> values, String value, String field) {
    if (!values.add(value)) {
      throw new InvalidCustomerEventException("Duplicate " + field + " in snapshot");
    }
  }

  private void object(JsonNode node, String field) {
    if (node == null || !node.isObject()) {
      throw new InvalidCustomerEventException(field + " must be a JSON object");
    }
  }
}
