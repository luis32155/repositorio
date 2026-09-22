package pe.com.scotiabank.tapp.customer.event.consumer.application.validation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.exception.InvalidCustomerEventException;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerEventEnvelope;
import tools.jackson.databind.JsonNode;

/** Validates relationship fields while allowing arbitrary additional fields in every section. */
@Component
public class CustomerEventValidator {

  public CustomerEventEnvelope validate(CustomerEventEnvelope event) {
    if (event == null) {
      throw invalid("Customer event is mandatory");
    }
    object(event.payload(), "payload");
    object(event.metadata(), "metadata");
    if (!"FULL".equals(requiredText(event.payload(), "snapshot_mode"))) {
      throw invalid("snapshot_mode must be FULL");
    }

    var identity = requiredObject(event.payload(), "customer_identity");
    var customerKey = customerKey(identity);
    requiredText(identity, "customer_type");
    requiredText(identity, "person_name");

    validateMetadata(event.metadata());
    validatePersonProfiles(event.payload(), customerKey);
    validateContacts(requiredArray(event.payload(), "digital_contacts"), customerKey);
    var accountKeys = validateAccounts(requiredArray(event.payload(), "account_profiles"));
    validateLinks(
        requiredArray(event.payload(), "customer_account_links"), customerKey, accountKeys);
    validateConsolidations(requiredArray(event.payload(), "account_consolidations"), accountKeys);
    return event;
  }

  private void validateMetadata(JsonNode metadata) {
    requiredText(metadata, "source_id");
    requiredText(metadata, "event_id");
    timestamp(requiredText(metadata, "event_timestamp"), "event_timestamp");
    if (metadata.hasNonNull("source_sequence")) {
      try {
        new BigDecimal(requiredText(metadata, "source_sequence"));
      } catch (NumberFormatException exception) {
        throw new InvalidCustomerEventException(
            "source_sequence must be a decimal number", exception);
      }
    }
  }

  private void validatePersonProfiles(JsonNode payload, String customerKey) {
    var individual = optionalObject(payload, "individual_profile");
    var legal = optionalObject(payload, "legal_entity_profile");
    if (individual == null && legal == null) {
      throw invalid("individual_profile or legal_entity_profile is required");
    }
    if (individual != null) {
      sameCustomer(individual, customerKey, "individual_profile");
      optionalDate(individual, "birth_date");
    }
    if (legal != null) {
      sameCustomer(legal, customerKey, "legal_entity_profile");
      optionalDate(legal, "constitution_date");
    }
  }

  private void validateContacts(JsonNode contacts, String customerKey) {
    Set<String> keys = new HashSet<>();
    contacts.forEach(
        contact -> {
          object(contact, "digital_contact");
          sameCustomer(contact, customerKey, "digital_contact");
          var key =
              customerKey
                  + '|'
                  + requiredText(contact, "contact_type")
                  + '|'
                  + requiredText(contact, "contact_value");
          unique(keys, key, "digital contact");
          requiredText(contact, "validation_status");
        });
  }

  private Set<String> validateAccounts(JsonNode accounts) {
    Set<String> keys = new HashSet<>();
    accounts.forEach(
        account -> {
          object(account, "account_profile");
          var key = accountKey(account, "company_code", "account_number");
          unique(keys, key, "account profile");
          optionalDate(account, "balance_confirmation_date");
          optionalDate(account, "opened_date");
          optionalDate(account, "closed_date");
        });
    return keys;
  }

  private void validateLinks(JsonNode links, String customerKey, Set<String> accountKeys) {
    Set<String> keys = new HashSet<>();
    links.forEach(
        link -> {
          object(link, "customer_account_link");
          sameCustomer(link, customerKey, "customer_account_link");
          var key = accountKey(link, "company_code", "account_number");
          unique(keys, key, "customer account link");
          referenced(accountKeys, key, "customer_account_link");
        });
  }

  private void validateConsolidations(JsonNode consolidations, Set<String> accountKeys) {
    Set<String> keys = new HashSet<>();
    consolidations.forEach(
        consolidation -> {
          object(consolidation, "account_consolidation");
          var source = accountKey(consolidation, "source_company_code", "source_account_number");
          var integrated =
              accountKey(consolidation, "integrated_company_code", "integrated_account_number");
          var key =
              requiredText(consolidation, "relationship_code") + '|' + source + '|' + integrated;
          unique(keys, key, "account consolidation");
          referenced(accountKeys, source, "account_consolidation source");
          referenced(accountKeys, integrated, "account_consolidation integrated account");
        });
  }

  private String customerKey(JsonNode node) {
    return requiredText(node, "country_code")
        + '|'
        + requiredText(node, "document_type")
        + '|'
        + requiredText(node, "document_number");
  }

  private String accountKey(JsonNode node, String companyField, String accountField) {
    return requiredText(node, companyField) + '|' + requiredText(node, accountField);
  }

  private void sameCustomer(JsonNode node, String expected, String section) {
    if (!customerKey(node).equals(expected)) {
      throw invalid(section + " document key does not match customer_identity");
    }
  }

  private void referenced(Set<String> values, String value, String section) {
    if (!values.contains(value)) {
      throw invalid(section + " references an account missing from account_profiles");
    }
  }

  private JsonNode optionalObject(JsonNode parent, String field) {
    if (!parent.hasNonNull(field)) {
      return null;
    }
    var value = parent.path(field);
    object(value, field);
    return value;
  }

  private JsonNode requiredObject(JsonNode parent, String field) {
    var value = parent.path(field);
    object(value, field);
    return value;
  }

  private JsonNode requiredArray(JsonNode parent, String field) {
    var value = parent.path(field);
    if (!value.isArray()) {
      throw invalid(field + " must be an array in a full snapshot");
    }
    return value;
  }

  private String requiredText(JsonNode node, String field) {
    var value = node.path(field);
    if ((!value.isTextual() && !value.isIntegralNumber()) || value.asText().isBlank()) {
      throw invalid(field + " must be a non-empty string or integer");
    }
    return value.asText();
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

  private void timestamp(String value, String field) {
    try {
      Instant.parse(value);
    } catch (DateTimeParseException exception) {
      throw new InvalidCustomerEventException(field + " must be a valid UTC instant", exception);
    }
  }

  private void unique(Set<String> values, String value, String label) {
    if (!values.add(value)) {
      throw invalid("Duplicate " + label + " in snapshot");
    }
  }

  private void object(JsonNode node, String field) {
    if (node == null || !node.isObject()) {
      throw invalid(field + " must be a JSON object");
    }
  }

  private InvalidCustomerEventException invalid(String message) {
    return new InvalidCustomerEventException(message);
  }
}
