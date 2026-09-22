package pe.com.scotiabank.tapp.customer.event.consumer.domain.model;

import tools.jackson.databind.JsonNode;

public record CustomerEventEnvelope(JsonNode payload, JsonNode metadata) {}
