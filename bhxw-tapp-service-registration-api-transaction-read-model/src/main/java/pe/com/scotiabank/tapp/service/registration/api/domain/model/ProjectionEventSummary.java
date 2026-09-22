package pe.com.scotiabank.tapp.service.registration.api.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ProjectionEventSummary(
    @JsonProperty("event_id") @NotBlank @Size(max = 45) String eventId,
    @JsonProperty("event_type") @NotNull EventType eventType,
    @JsonProperty("aggregate_version") @Positive long aggregateVersion,
    @JsonProperty("occurred_at") @NotNull Instant occurredAt) {}
