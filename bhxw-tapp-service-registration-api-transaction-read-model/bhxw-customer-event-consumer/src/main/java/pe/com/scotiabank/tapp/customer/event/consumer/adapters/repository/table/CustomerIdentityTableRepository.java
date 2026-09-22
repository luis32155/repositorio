package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerIdentityEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import reactor.core.publisher.Mono;

public interface CustomerIdentityTableRepository
    extends ReactiveCrudRepository<CustomerIdentityEntity, UUID> {

  default Mono<UUID> upsertIfNewer(CustomerSnapshot snapshot, Instant now) {
    return upsertRow(
        snapshot.sourceId(),
        snapshot.identity().toString(),
        snapshot.eventId(),
        snapshot.sourceSequence(),
        snapshot.payload().toString(),
        snapshot.metadata().toString(),
        now);
  }

  @Query(
      """
      INSERT INTO cdc_customer.customer_identity
        (source_id, country_code, document_type, document_number, customer_type,
         person_name, source_event_id, source_sequence, raw_payload, event_metadata,
         created_at, updated_at)
      SELECT :sourceId, CAST(i->>'country_code' AS numeric), CAST(i->>'document_type' AS numeric),
        i->>'document_number', i->>'customer_type', i->>'person_name',
        :eventId, :sourceSequence, CAST(:payload AS jsonb), CAST(:metadata AS jsonb), :now, :now
      FROM (SELECT CAST(:identity AS jsonb) AS i) incoming
      ON CONFLICT (source_id, country_code, document_type, document_number)
      DO UPDATE SET
        customer_type = EXCLUDED.customer_type,
        person_name = EXCLUDED.person_name,
        source_event_id = EXCLUDED.source_event_id,
        source_sequence = EXCLUDED.source_sequence,
        raw_payload = EXCLUDED.raw_payload,
        event_metadata = EXCLUDED.event_metadata,
        updated_at = EXCLUDED.updated_at
      WHERE EXCLUDED.source_sequence > cdc_customer.customer_identity.source_sequence
      RETURNING customer_identity_key
      """)
  Mono<UUID> upsertRow(
      @Param("sourceId") String sourceId,
      @Param("identity") String identity,
      @Param("eventId") String eventId,
      @Param("sourceSequence") BigDecimal sourceSequence,
      @Param("payload") String payload,
      @Param("metadata") String metadata,
      @Param("now") Instant now);
}
