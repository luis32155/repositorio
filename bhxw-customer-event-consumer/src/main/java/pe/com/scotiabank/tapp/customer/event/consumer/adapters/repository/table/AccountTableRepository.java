package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.AccountEntity;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface AccountTableRepository extends ReactiveCrudRepository<AccountEntity, UUID> {

  default Mono<UUID> upsert(String sourceId, String enterpriseId, JsonNode account, Instant now) {
    return upsertRow(sourceId, enterpriseId, account.toString(), now);
  }

  @Query(
      """
      INSERT INTO cdc_customer.accounts
        (source_id, enterprise_sco_id, account_number, account_type, account_name,
         account_status, currency_code, account_attributes, created_at, updated_at)
      SELECT :source, :enterprise, p->>'account_number', p->>'account_type',
        p->>'account_name', p->>'account_status', p->>'currency_code', p, :now, :now
      FROM (SELECT CAST(:payload AS jsonb) AS p) incoming
      ON CONFLICT (source_id, enterprise_sco_id, account_number) DO UPDATE SET
        account_type = EXCLUDED.account_type,
        account_name = EXCLUDED.account_name,
        account_status = EXCLUDED.account_status,
        currency_code = EXCLUDED.currency_code,
        account_attributes = EXCLUDED.account_attributes,
        updated_at = EXCLUDED.updated_at
      RETURNING account_key
      """)
  Mono<UUID> upsertRow(
      @Param("source") String sourceId,
      @Param("enterprise") String enterpriseId,
      @Param("payload") String payload,
      @Param("now") Instant now);
}
