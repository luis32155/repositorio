package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ExistingCustomerSnapshot(
    UUID enterpriseUserKey, UUID userKey, BigDecimal ordering, boolean samePayload) {}
