package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.UserServiceEntity;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface UserServiceTableRepository
    extends ReactiveCrudRepository<UserServiceEntity, UUID> {

  default Mono<Void> deleteAll(UUID enterpriseUserKey) {
    return deleteAllRows(enterpriseUserKey).then();
  }

  @Modifying
  @Query(
      """
      DELETE FROM cdc_customer.user_services
      WHERE enterprise_user_key = :enterpriseUserKey
      """)
  Mono<Integer> deleteAllRows(@Param("enterpriseUserKey") UUID enterpriseUserKey);

  default Mono<UUID> insert(
      String sourceId,
      String enterpriseId,
      UUID enterpriseUserKey,
      UUID serviceKey,
      JsonNode service,
      int order,
      Instant now) {
    return insertRow(
        sourceId, enterpriseId, enterpriseUserKey, serviceKey, service.toString(), order, now);
  }

  @Query(
      """
      INSERT INTO cdc_customer.user_services
        (source_id, enterprise_sco_id, enterprise_user_key, service_key,
         service_selected, user_role, transaction_limit, daily_limit,
         limit_currency, service_order, entitlement_attributes, created_at, updated_at)
      SELECT :source, :enterprise, :enterpriseUserKey, :serviceKey,
        CAST(p->>'service_selected' AS boolean), p->>'user_role',
        CAST(p#>>'{service_details,transaction_limit}' AS numeric),
        CAST(p#>>'{service_details,daily_limit}' AS numeric),
        p#>>'{service_details,limit_currency}', :serviceOrder, p, :now, :now
      FROM (SELECT CAST(:payload AS jsonb) AS p) incoming
      RETURNING user_service_key
      """)
  Mono<UUID> insertRow(
      @Param("source") String sourceId,
      @Param("enterprise") String enterpriseId,
      @Param("enterpriseUserKey") UUID enterpriseUserKey,
      @Param("serviceKey") UUID serviceKey,
      @Param("payload") String payload,
      @Param("serviceOrder") int order,
      @Param("now") Instant now);
}
