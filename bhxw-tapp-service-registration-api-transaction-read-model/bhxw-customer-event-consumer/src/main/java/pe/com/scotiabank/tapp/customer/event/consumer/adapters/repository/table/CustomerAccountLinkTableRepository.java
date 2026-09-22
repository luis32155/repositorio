package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerAccountLinkEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface CustomerAccountLinkTableRepository
    extends ReactiveCrudRepository<CustomerAccountLinkEntity, UUID> {

  default Mono<Void> deleteFor(CustomerSnapshot snapshot) {
    return deleteRows(
            snapshot.sourceId(),
            snapshot.countryCode(),
            snapshot.documentType(),
            snapshot.documentNumber())
        .then();
  }

  default Mono<Void> insert(String sourceId, JsonNode link, Instant now) {
    return insertRow(sourceId, link.toString(), now).then();
  }

  @Modifying
  @Query(
      """
      DELETE FROM cdc_customer.customer_account_link
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
      INSERT INTO cdc_customer.customer_account_link
        (source_id, company_code, account_number, country_code, document_type,
         document_number, attributes, created_at, updated_at)
      SELECT :sourceId, CAST(l->>'company_code' AS numeric), CAST(l->>'account_number' AS numeric),
        CAST(l->>'country_code' AS numeric), CAST(l->>'document_type' AS numeric),
        l->>'document_number', l, :now, :now
      FROM (SELECT CAST(:link AS jsonb) AS l) incoming
      RETURNING customer_account_link_key
      """)
  Mono<UUID> insertRow(
      @Param("sourceId") String sourceId, @Param("link") String link, @Param("now") Instant now);
}
