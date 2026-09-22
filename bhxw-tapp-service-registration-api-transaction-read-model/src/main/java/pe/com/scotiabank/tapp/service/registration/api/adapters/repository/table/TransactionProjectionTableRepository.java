package pe.com.scotiabank.tapp.service.registration.api.adapters.repository.table;

import java.time.Instant;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.entity.TransactionProjectionEntity;
import pe.com.scotiabank.tapp.service.registration.api.adapters.repository.model.ExistingProjection;
import reactor.core.publisher.Mono;

public interface TransactionProjectionTableRepository
    extends ReactiveCrudRepository<TransactionProjectionEntity, String> {

  @Query(
      """
            INSERT INTO transaction_read_model.transaction_projection
              (document_id, transaction_id, req_msg_id, transaction_status,
               callback_status, notification_status, log_audit_status, amount,
               currency, account_reference, masked_account, started_at, completed_at,
               last_event_id, last_event_type, aggregate_version, last_event_occurred_at,
               projection_payload, created_at, updated_at, expires_at)
            SELECT :documentId, p->>'transaction_id', p->>'req_msg_id',
              p->>'transaction_status', p->>'callback_status', p->>'notification_status',
              p->>'log_audit_status', CAST(p->>'amount' AS numeric), p->>'currency',
              p->>'account_reference', p->>'masked_account',
              CAST(p->>'started_at' AS timestamptz),
              CAST(NULLIF(p->>'completed_at', '') AS timestamptz),
              :eventId, p->>'event_type', CAST(p->>'aggregate_version' AS bigint),
              CAST(p->>'occurred_at' AS timestamptz), p, :now, :now, :expiresAt
            FROM (SELECT CAST(:payload AS jsonb) AS p) incoming
            ON CONFLICT (document_id) DO UPDATE SET
              transaction_status = EXCLUDED.transaction_status,
              callback_status = EXCLUDED.callback_status,
              notification_status = EXCLUDED.notification_status,
              log_audit_status = EXCLUDED.log_audit_status,
              amount = EXCLUDED.amount,
              currency = EXCLUDED.currency,
              account_reference = EXCLUDED.account_reference,
              masked_account = EXCLUDED.masked_account,
              started_at = EXCLUDED.started_at,
              completed_at = EXCLUDED.completed_at,
              last_event_id = EXCLUDED.last_event_id,
              last_event_type = EXCLUDED.last_event_type,
              aggregate_version = EXCLUDED.aggregate_version,
              last_event_occurred_at = EXCLUDED.last_event_occurred_at,
              projection_payload = EXCLUDED.projection_payload,
              updated_at = EXCLUDED.updated_at,
              expires_at = EXCLUDED.expires_at
            WHERE EXCLUDED.aggregate_version >
              transaction_read_model.transaction_projection.aggregate_version
            RETURNING aggregate_version
            """)
  Mono<Long> upsertIfNewer(
      @Param("documentId") String documentId,
      @Param("eventId") String eventId,
      @Param("payload") String payload,
      @Param("now") Instant now,
      @Param("expiresAt") Instant expiresAt);

  @Query(
      """
            SELECT last_event_id, aggregate_version
            FROM transaction_read_model.transaction_projection
            WHERE document_id = :documentId
            """)
  Mono<ExistingProjection> findState(@Param("documentId") String documentId);
}
