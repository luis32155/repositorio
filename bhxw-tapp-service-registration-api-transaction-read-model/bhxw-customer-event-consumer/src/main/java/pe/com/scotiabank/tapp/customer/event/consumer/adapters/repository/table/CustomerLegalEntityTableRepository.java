package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerLegalEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import reactor.core.publisher.Mono;

public interface CustomerLegalEntityTableRepository
    extends ReactiveCrudRepository<CustomerLegalEntity, UUID> {

  default Mono<Void> deleteFor(CustomerSnapshot snapshot) {
    return deleteRows(
            snapshot.sourceId(),
            snapshot.countryCode(),
            snapshot.documentType(),
            snapshot.documentNumber())
        .then();
  }

  default Mono<Void> insert(CustomerSnapshot snapshot, Instant now) {
    return insertRow(snapshot.sourceId(), snapshot.legalEntity().toString(), now).then();
  }

  @Modifying
  @Query(
      """
      DELETE FROM cdc_customer.customer_legal_entity
      WHERE source_id = :sourceId AND country_code = CAST(:countryCode AS numeric)
        AND document_type = CAST(:documentType AS numeric) AND document_number = :documentNumber
      """)
  Mono<Integer> deleteRows(
      @Param("sourceId") String sourceId,
      @Param("countryCode") String countryCode,
      @Param("documentType") String documentType,
      @Param("documentNumber") String documentNumber);

  @Query(
      """
      INSERT INTO cdc_customer.customer_legal_entity
        (source_id, country_code, document_type, document_number, legal_name,
         constitution_date, attributes, created_at, updated_at)
      SELECT :sourceId, CAST(p->>'country_code' AS numeric), CAST(p->>'document_type' AS numeric),
        p->>'document_number', p->>'legal_name',
        CAST(NULLIF(p->>'constitution_date', '') AS date), p, :now, :now
      FROM (SELECT CAST(:profile AS jsonb) AS p) incoming
      RETURNING customer_legal_entity_key
      """)
  Mono<UUID> insertRow(
      @Param("sourceId") String sourceId,
      @Param("profile") String profile,
      @Param("now") Instant now);
}
