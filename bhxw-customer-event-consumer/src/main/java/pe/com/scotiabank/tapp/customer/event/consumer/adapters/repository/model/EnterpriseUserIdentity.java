package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.model;

import java.util.UUID;

public record EnterpriseUserIdentity(UUID enterpriseUserKey, UUID userKey) {}
