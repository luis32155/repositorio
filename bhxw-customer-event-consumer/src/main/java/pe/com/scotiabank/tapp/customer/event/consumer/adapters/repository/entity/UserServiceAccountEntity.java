package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity;

import io.r2dbc.postgresql.codec.Json;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "user_service_accounts", schema = "cdc_customer")
public record UserServiceAccountEntity(
    @Id UUID userServiceAccountKey,
    String sourceId,
    String enterpriseScoId,
    UUID userServiceKey,
    UUID accountKey,
    BigDecimal transactionLimit,
    BigDecimal dailyLimit,
    Integer accountOrder,
    Json entitlementAttributes,
    Instant createdAt,
    Instant updatedAt) {}
