package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity;

import io.r2dbc.postgresql.codec.Json;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "services", schema = "cdc_customer")
public record ServiceEntity(
    @Id UUID serviceKey,
    String sourceId,
    String enterpriseScoId,
    String serviceId,
    String serviceName,
    String serviceType,
    Json serviceAttributes,
    Instant createdAt,
    Instant updatedAt) {}
