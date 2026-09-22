package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity;

import io.r2dbc.postgresql.codec.Json;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "accounts", schema = "cdc_customer")
public record AccountEntity(
    @Id UUID accountKey,
    String sourceId,
    String enterpriseScoId,
    String accountNumber,
    String accountType,
    String accountName,
    String accountStatus,
    String currencyCode,
    Json accountAttributes,
    Instant createdAt,
    Instant updatedAt) {}
