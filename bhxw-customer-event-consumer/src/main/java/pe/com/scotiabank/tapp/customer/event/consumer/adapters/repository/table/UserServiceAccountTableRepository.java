package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.UserServiceAccountEntity;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface UserServiceAccountTableRepository
    extends ReactiveCrudRepository<UserServiceAccountEntity, UUID> {

  default Mono<Void> insert(
      String sourceId,
      String enterpriseId,
      UUID userServiceKey,
      UUID accountKey,
      JsonNode account,
      int order,
      Instant now) {
    return insertRow(
            sourceId, enterpriseId, userServiceKey, accountKey, account.toString(), order, now)
        .then();
  }

  @Modifying
  @Query(
      """
      INSERT INTO cdc_customer.user_service_accounts
        (source_id, enterprise_sco_id, user_service_key, account_key,
         transaction_limit, daily_limit, account_order,
         entitlement_attributes, created_at, updated_at)
      SELECT :source, :enterprise, :userServiceKey, :accountKey,
        CAST(p->>'transaction_limit' AS numeric), CAST(p->>'daily_limit' AS numeric),
        :accountOrder, p, :now, :now
      FROM (SELECT CAST(:payload AS jsonb) AS p) incoming
      """)
  Mono<Integer> insertRow(
      @Param("source") String sourceId,
      @Param("enterprise") String enterpriseId,
      @Param("userServiceKey") UUID userServiceKey,
      @Param("accountKey") UUID accountKey,
      @Param("payload") String payload,
      @Param("accountOrder") int order,
      @Param("now") Instant now);
}
