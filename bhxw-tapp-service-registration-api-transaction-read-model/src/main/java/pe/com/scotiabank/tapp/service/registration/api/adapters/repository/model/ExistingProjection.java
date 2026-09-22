package pe.com.scotiabank.tapp.service.registration.api.adapters.repository.model;

public record ExistingProjection(String lastEventId, long aggregateVersion) {}
