package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerAccountConsolidationEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface CustomerAccountConsolidationTableRepository
    extends ReactiveCrudRepository<CustomerAccountConsolidationEntity, UUID> {

  default Mono<Void> deleteFor(CustomerSnapshot snapshot) {
    return deleteRows(
            snapshot.sourceId(),
            snapshot.countryCode(),
            snapshot.documentType(),
            snapshot.documentNumber())
        .then();
  }

  default Mono<Void> insert(String sourceId, JsonNode consolidation, Instant now) {
    return insertRow(sourceId, consolidation.toString(), now).then();
  }

  @Modifying
  @Query(
      """
      DELETE FROM cdc_customer.customer_account_consolidation c
      USING cdc_customer.customer_account_link l
      WHERE l.source_id = :sourceId
        AND l.country_code = CAST(:countryCode AS numeric)
        AND l.document_type = CAST(:documentType AS numeric)
        AND l.document_number = :documentNumber
        AND c.source_id = l.source_id
        AND c.source_company_code = l.company_code
        AND c.source_account_number = l.account_number
      """)
  Mono<Integer> deleteRows(
      @Param("sourceId") String sourceId,
      @Param("countryCode") String countryCode,
      @Param("documentType") String documentType,
      @Param("documentNumber") String documentNumber);

  @Query(
      """
      INSERT INTO cdc_customer.customer_account_consolidation
        (source_id, relationship_code, integrated_company_code, integrated_account_number,
         source_company_code, source_account_number, attributes, created_at, updated_at)
      SELECT :sourceId, CAST(c->>'relationship_code' AS numeric),
        CAST(c->>'integrated_company_code' AS numeric),
        CAST(c->>'integrated_account_number' AS numeric),
        CAST(c->>'source_company_code' AS numeric),
        CAST(c->>'source_account_number' AS numeric), c, :now, :now
      FROM (SELECT CAST(:consolidation AS jsonb) AS c) incoming
      RETURNING customer_account_consolidation_key
      """)
  Mono<UUID> insertRow(
      @Param("sourceId") String sourceId,
      @Param("consolidation") String consolidation,
      @Param("now") Instant now);
}
