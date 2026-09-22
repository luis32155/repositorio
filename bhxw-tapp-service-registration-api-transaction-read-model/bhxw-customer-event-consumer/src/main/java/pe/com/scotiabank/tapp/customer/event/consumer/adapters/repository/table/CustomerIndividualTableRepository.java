package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerIndividualEntity;
import pe.com.scotiabank.tapp.customer.event.consumer.domain.model.CustomerSnapshot;
import reactor.core.publisher.Mono;

public interface CustomerIndividualTableRepository
    extends ReactiveCrudRepository<CustomerIndividualEntity, UUID> {

  default Mono<Void> deleteFor(CustomerSnapshot snapshot) {
    return deleteRows(
            snapshot.sourceId(),
            snapshot.countryCode(),
            snapshot.documentType(),
            snapshot.documentNumber())
        .then();
  }

  default Mono<Void> insert(CustomerSnapshot snapshot, Instant now) {
    return insertRow(snapshot.sourceId(), snapshot.individual().toString(), now).then();
  }

  @Modifying
  @Query(
      """
      DELETE FROM cdc_customer.customer_individual
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
      INSERT INTO cdc_customer.customer_individual
        (source_id, country_code, document_type, document_number, first_last_name,
         second_last_name, first_given_name, second_given_name, birth_date,
         marital_status, gender, attributes, created_at, updated_at)
      SELECT :sourceId, CAST(p->>'country_code' AS numeric), CAST(p->>'document_type' AS numeric),
        p->>'document_number', p->>'first_last_name', p->>'second_last_name',
        p->>'first_given_name', p->>'second_given_name',
        CAST(NULLIF(p->>'birth_date', '') AS date), p->>'marital_status', p->>'gender',
        p, :now, :now
      FROM (SELECT CAST(:profile AS jsonb) AS p) incoming
      RETURNING customer_individual_key
      """)
  Mono<UUID> insertRow(
      @Param("sourceId") String sourceId,
      @Param("profile") String profile,
      @Param("now") Instant now);
}
