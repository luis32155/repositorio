package pe.com.scotiabank.tapp.service.registration.api.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EventMetadata(
    @JsonProperty("event_id") @NotBlank @Size(max = 45) String eventId,
    @JsonProperty("schema_version") @NotBlank @Size(max = 10) @Pattern(regexp = "^\\d+\\.\\d+$")
        String schemaVersion,
    @JsonProperty("published_at") @NotNull Instant publishedAt,
    @JsonProperty("source_service") @NotBlank @Size(max = 100) String sourceService,
    @JsonProperty("correlation_id") @NotBlank @Size(max = 100) String correlationId,
    @JsonProperty("trace_id") @Size(max = 100) String traceId,
    @JsonProperty("payload_signature") @Size(max = 4096) String payloadSignature,
    @JsonProperty("key_id") @Size(max = 100) String keyId,
    @JsonProperty("retry_count") @Min(0) @Max(3) Integer retryCount) {}
