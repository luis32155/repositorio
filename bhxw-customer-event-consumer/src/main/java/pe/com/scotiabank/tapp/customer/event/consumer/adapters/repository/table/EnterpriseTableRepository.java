package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.EnterpriseEntity;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface EnterpriseTableRepository extends ReactiveCrudRepository<EnterpriseEntity, UUID> {

  default Mono<Void> upsert(
      String sourceId, String enterpriseId, JsonNode enterprise, Instant now) {
    return upsertRow(sourceId, enterpriseId, enterprise.toString(), now).then();
  }

  @Modifying
  @Query(
      """
      INSERT INTO cdc_customer.enterprises
        (source_id, enterprise_sco_id, reference_code, enterprise_name,
         enterprise_country, enterprise_status, constitution_date,
         enterprise_attributes, updated_at)
      SELECT :source, :enterprise, p->>'reference_code', p->>'enterprise_name',
        p->>'enterprise_country', p->>'enterprise_status',
        CAST(p->>'constitution_date' AS date), p, :now
      FROM (SELECT CAST(:payload AS jsonb) AS p) incoming
      ON CONFLICT (source_id, enterprise_sco_id) DO UPDATE SET
        reference_code = EXCLUDED.reference_code,
        enterprise_name = EXCLUDED.enterprise_name,
        enterprise_country = EXCLUDED.enterprise_country,
        enterprise_status = EXCLUDED.enterprise_status,
        constitution_date = COALESCE(EXCLUDED.constitution_date,
                                     cdc_customer.enterprises.constitution_date),
        enterprise_attributes = EXCLUDED.enterprise_attributes,
        updated_at = EXCLUDED.updated_at
      """)
  Mono<Integer> upsertRow(
      @Param("source") String sourceId,
      @Param("enterprise") String enterpriseId,
      @Param("payload") String payload,
      @Param("now") Instant now);
}
