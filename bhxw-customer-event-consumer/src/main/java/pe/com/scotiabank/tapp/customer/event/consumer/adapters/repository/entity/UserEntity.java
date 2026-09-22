package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity;

import io.r2dbc.postgresql.codec.Json;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "users", schema = "cdc_customer")
public record UserEntity(
    @Id UUID userKey,
    String userFullName,
    String userMiddleName,
    String userCountry,
    String userPreferredLanguage,
    String userEmail,
    String userEmailDomain,
    String phoneNumber,
    Integer documentType,
    String documentNumber,
    String personType,
    LocalDate birthDate,
    String maritalStatus,
    String gender,
    Json userAttributes,
    Instant createdAt,
    Instant updatedAt) {}
