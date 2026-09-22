package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerDigitalContactEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface CustomerDigitalContactTableRepository
    extends ReactiveCrudRepository<CustomerDigitalContactEntity, UUID> {

  default Mono<Void> deleteFor(CustomerSnapshot snapshot) {
    return deleteRows(
            snapshot.sourceId(),
            snapshot.countryCode(),
            snapshot.documentType(),
            snapshot.documentNumber())
        .then();
  }

  default Mono<Void> insert(String sourceId, JsonNode contact, Instant now) {
    return insertRow(sourceId, contact.toString(), now).then();
  }

  @Modifying
  @Query(
      """
      DELETE FROM cdc_customer.customer_digital_contact
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
      INSERT INTO cdc_customer.customer_digital_contact
        (source_id, country_code, document_type, document_number, contact_type,
         contact_value, validation_status, attributes, created_at, updated_at)
      SELECT :sourceId, CAST(c->>'country_code' AS numeric), CAST(c->>'document_type' AS numeric),
        c->>'document_number', c->>'contact_type', c->>'contact_value',
        c->>'validation_status', c, :now, :now
      FROM (SELECT CAST(:contact AS jsonb) AS c) incoming
      RETURNING customer_digital_contact_key
      """)
  Mono<UUID> insertRow(
      @Param("sourceId") String sourceId,
      @Param("contact") String contact,
      @Param("now") Instant now);
}
