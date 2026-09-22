package pe.com.scotiabank.tapp.service.registration.api.domain.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TransactionProjectionEvent(
    @NotNull @Valid TransactionProjectionPayload payload, @NotNull @Valid EventMetadata metadata) {}
