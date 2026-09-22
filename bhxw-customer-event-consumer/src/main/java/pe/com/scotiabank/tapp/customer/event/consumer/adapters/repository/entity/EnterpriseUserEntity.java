package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity;

import io.r2dbc.postgresql.codec.Json;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "enterprise_users", schema = "cdc_customer")
public record EnterpriseUserEntity(
    @Id UUID enterpriseUserKey,
    String sourceId,
    String enterpriseScoId,
    UUID userKey,
    String externalUserId,
    String userRoleName,
    String userStatus,
    String userLanguage,
    Instant lastSigninTimestamp,
    Boolean accountTransferEnabled,
    Boolean recipientMaintenanceEnabled,
    UUID triggeredByEnterpriseUserKey,
    String eventName,
    Instant snapshotAt,
    BigDecimal snapshotOrder,
    Json payloadSnapshot,
    Json eventMetadata,
    Instant createdAt,
    Instant updatedAt) {}
