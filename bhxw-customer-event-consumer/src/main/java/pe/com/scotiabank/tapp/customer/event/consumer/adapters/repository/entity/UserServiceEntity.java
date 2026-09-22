package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity;

import io.r2dbc.postgresql.codec.Json;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "user_services", schema = "cdc_customer")
public record UserServiceEntity(
    @Id UUID userServiceKey,
    String sourceId,
    String enterpriseScoId,
    UUID enterpriseUserKey,
    UUID serviceKey,
    Boolean serviceSelected,
    String userRole,
    BigDecimal transactionLimit,
    BigDecimal dailyLimit,
    String limitCurrency,
    Integer serviceOrder,
    Json entitlementAttributes,
    Instant createdAt,
    Instant updatedAt) {}
