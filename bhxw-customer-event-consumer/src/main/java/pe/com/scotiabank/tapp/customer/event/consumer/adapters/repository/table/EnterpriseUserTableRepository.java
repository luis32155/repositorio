package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.EnterpriseUserEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.model.EnterpriseUserIdentity;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.model.ExistingCustomerSnapshot;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface EnterpriseUserTableRepository
    extends ReactiveCrudRepository<EnterpriseUserEntity, UUID> {
  String SOURCE = "source";
  String ENTERPRISE = "enterprise";
  String EXTERNAL_USER = "externalUser";

  @Query(
      """
      SELECT enterprise_user_key, user_key
      FROM cdc_customer.enterprise_users
      WHERE source_id = :source AND enterprise_sco_id = :enterprise
        AND external_user_id = :externalUser
      """)
  Mono<EnterpriseUserIdentity> findIdentity(
      @Param(SOURCE) String sourceId,
      @Param(ENTERPRISE) String enterpriseId,
      @Param(EXTERNAL_USER) String externalUserId);

  default Mono<ExistingCustomerSnapshot> findSnapshot(CustomerSnapshot snapshot) {
    return findSnapshotRow(
        snapshot.sourceId(),
        snapshot.enterpriseId(),
        snapshot.userId(),
        snapshot.event().payload().toString());
  }

  @Query(
      """
      SELECT enterprise_user_key, user_key, snapshot_order AS ordering,
        payload_snapshot = CAST(:payload AS jsonb) AS same_payload
      FROM cdc_customer.enterprise_users
      WHERE source_id = :source AND enterprise_sco_id = :enterprise
        AND external_user_id = :externalUser
      """)
  Mono<ExistingCustomerSnapshot> findSnapshotRow(
      @Param(SOURCE) String sourceId,
      @Param(ENTERPRISE) String enterpriseId,
      @Param(EXTERNAL_USER) String externalUserId,
      @Param("payload") String payload);

  default Mono<UUID> saveActor(
      String sourceId,
      String enterpriseId,
      String externalUserId,
      UUID userKey,
      JsonNode actor,
      Instant now) {
    return saveActorRow(sourceId, enterpriseId, externalUserId, userKey, actor.toString(), now);
  }

  @Query(
      """
      INSERT INTO cdc_customer.enterprise_users
        (source_id, enterprise_sco_id, user_key, external_user_id, user_role_name,
         user_status, user_language, last_signin_timestamp, created_at, updated_at)
      SELECT :source, :enterprise, :userKey, :externalUser, p->>'user_role_name',
        p->>'user_status', p->>'user_language',
        CAST(p->>'last_signin_timestamp' AS timestamptz), :now, :now
      FROM (SELECT CAST(:payload AS jsonb) AS p) incoming
      ON CONFLICT (source_id, enterprise_sco_id, external_user_id) DO UPDATE SET
        user_role_name = EXCLUDED.user_role_name,
        user_status = EXCLUDED.user_status,
        user_language = EXCLUDED.user_language,
        last_signin_timestamp = EXCLUDED.last_signin_timestamp,
        updated_at = EXCLUDED.updated_at
      RETURNING enterprise_user_key
      """)
  Mono<UUID> saveActorRow(
      @Param(SOURCE) String sourceId,
      @Param(ENTERPRISE) String enterpriseId,
      @Param(EXTERNAL_USER) String externalUserId,
      @Param("userKey") UUID userKey,
      @Param("payload") String payload,
      @Param("now") Instant now);

  default Mono<UUID> saveTarget(
      CustomerSnapshot snapshot, UUID userKey, UUID triggeringUserKey, Instant now) {
    return saveTargetRow(
        snapshot.sourceId(),
        snapshot.enterpriseId(),
        snapshot.userId(),
        userKey,
        triggeringUserKey,
        snapshot.eventName(),
        snapshot.timestamp(),
        snapshot.orderValue(),
        snapshot.user().toString(),
        snapshot.event().payload().toString(),
        snapshot.event().metadata().toString(),
        now);
  }

  @Query(
      """
      INSERT INTO cdc_customer.enterprise_users
        (source_id, enterprise_sco_id, user_key, external_user_id, user_role_name,
         user_status, user_language, account_transfer_enabled,
         recipient_maintenance_enabled, triggered_by_enterprise_user_key,
         event_name, snapshot_at, snapshot_order, payload_snapshot, event_metadata,
         created_at, updated_at)
      SELECT :source, :enterprise, :userKey, :externalUser, u->>'user_role_name',
        u->>'user_status', u->>'user_preferred_language',
        CAST(u#>>'{entitlements,account_transfer,enabled}' AS boolean),
        CAST(u#>>'{entitlements,recipient_maintenance_enabled}' AS boolean),
        :actorKey, :eventName, :snapshotAt, :snapshotOrder,
        CAST(:payload AS jsonb), CAST(:metadata AS jsonb), :now, :now
      FROM (SELECT CAST(:userPayload AS jsonb) AS u) incoming
      ON CONFLICT (source_id, enterprise_sco_id, external_user_id) DO UPDATE SET
        user_role_name = EXCLUDED.user_role_name,
        user_status = EXCLUDED.user_status,
        user_language = EXCLUDED.user_language,
        account_transfer_enabled = EXCLUDED.account_transfer_enabled,
        recipient_maintenance_enabled = EXCLUDED.recipient_maintenance_enabled,
        triggered_by_enterprise_user_key = EXCLUDED.triggered_by_enterprise_user_key,
        event_name = EXCLUDED.event_name,
        snapshot_at = EXCLUDED.snapshot_at,
        snapshot_order = EXCLUDED.snapshot_order,
        payload_snapshot = EXCLUDED.payload_snapshot,
        event_metadata = EXCLUDED.event_metadata,
        updated_at = EXCLUDED.updated_at
      RETURNING enterprise_user_key
      """)
  Mono<UUID> saveTargetRow(
      @Param(SOURCE) String sourceId,
      @Param(ENTERPRISE) String enterpriseId,
      @Param(EXTERNAL_USER) String externalUserId,
      @Param("userKey") UUID userKey,
      @Param("actorKey") UUID triggeringUserKey,
      @Param("eventName") String eventName,
      @Param("snapshotAt") Instant snapshotAt,
      @Param("snapshotOrder") java.math.BigDecimal snapshotOrder,
      @Param("userPayload") String userPayload,
      @Param("payload") String payload,
      @Param("metadata") String metadata,
      @Param("now") Instant now);
}
