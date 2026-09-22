package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity;

import io.r2dbc.postgresql.codec.Json;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "enterprises", schema = "cdc_customer")
public record EnterpriseEntity(
    @Id UUID enterpriseKey,
    String sourceId,
    String enterpriseScoId,
    String referenceCode,
    String enterpriseName,
    String enterpriseCountry,
    String enterpriseStatus,
    LocalDate constitutionDate,
    Json enterpriseAttributes,
    Instant updatedAt) {}
