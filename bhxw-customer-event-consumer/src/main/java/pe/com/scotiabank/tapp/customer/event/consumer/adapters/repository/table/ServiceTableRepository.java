package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.ServiceEntity;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface ServiceTableRepository extends ReactiveCrudRepository<ServiceEntity, UUID> {

  default Mono<UUID> upsert(String sourceId, String enterpriseId, JsonNode service, Instant now) {
    return upsertRow(sourceId, enterpriseId, service.toString(), now);
  }

  @Query(
      """
      INSERT INTO cdc_customer.services
        (source_id, enterprise_sco_id, service_id, service_name, service_type,
         service_attributes, created_at, updated_at)
      SELECT :source, :enterprise, p->>'service_id', p->>'service_name',
        p->>'service_type', p, :now, :now
      FROM (SELECT CAST(:payload AS jsonb) AS p) incoming
      ON CONFLICT (source_id, enterprise_sco_id, service_id) DO UPDATE SET
        service_name = EXCLUDED.service_name,
        service_type = EXCLUDED.service_type,
        service_attributes = EXCLUDED.service_attributes,
        updated_at = EXCLUDED.updated_at
      RETURNING service_key
      """)
  Mono<UUID> upsertRow(
      @Param("source") String sourceId,
      @Param("enterprise") String enterpriseId,
      @Param("payload") String payload,
      @Param("now") Instant now);
}
