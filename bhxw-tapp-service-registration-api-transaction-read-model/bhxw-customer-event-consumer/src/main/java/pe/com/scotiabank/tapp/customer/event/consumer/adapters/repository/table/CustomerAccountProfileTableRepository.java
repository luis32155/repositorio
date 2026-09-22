package pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.table;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import pe.com.scotiabank.tapp.customer.event.consumer.adapters.repository.entity.CustomerAccountProfileEntity;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface CustomerAccountProfileTableRepository
    extends ReactiveCrudRepository<CustomerAccountProfileEntity, UUID> {

  default Mono<UUID> upsert(String sourceId, String eventId, JsonNode account, Instant now) {
    return upsertRow(sourceId, eventId, account.toString(), now);
  }

  @Query(
      """
      INSERT INTO cdc_customer.customer_account_profile
        (source_id, company_code, account_number, account_officer_code,
         import_export_account_number, risk_classification, internal_classification_code,
         employee_indicator, supplier_indicator, balance_confirmation_date,
         customer_segment_code, corporate_account_number, account_name, resident_indicator,
         manager_visibility_level, activity_code, financial_institution_indicator,
         opened_date, closed_date, hold_correspondence_indicator, tax_exempt_indicator,
         pin_number, sector_code, source_event_id, attributes, created_at, updated_at)
      SELECT :sourceId, CAST(a->>'company_code' AS numeric), CAST(a->>'account_number' AS numeric),
        CAST(NULLIF(a->>'account_officer_code', '') AS numeric),
        CAST(NULLIF(a->>'import_export_account_number', '') AS numeric),
        CAST(NULLIF(a->>'risk_classification', '') AS numeric),
        CAST(NULLIF(a->>'internal_classification_code', '') AS numeric),
        a->>'employee_indicator', a->>'supplier_indicator',
        CAST(NULLIF(a->>'balance_confirmation_date', '') AS date),
        CAST(NULLIF(a->>'customer_segment_code', '') AS numeric),
        CAST(NULLIF(a->>'corporate_account_number', '') AS numeric),
        a->>'account_name', a->>'resident_indicator',
        CAST(NULLIF(a->>'manager_visibility_level', '') AS numeric),
        CAST(NULLIF(a->>'activity_code', '') AS numeric),
        a->>'financial_institution_indicator',
        CAST(NULLIF(a->>'opened_date', '') AS date), CAST(NULLIF(a->>'closed_date', '') AS date),
        a->>'hold_correspondence_indicator', a->>'tax_exempt_indicator',
        CAST(NULLIF(a->>'pin_number', '') AS numeric),
        CAST(NULLIF(a->>'sector_code', '') AS numeric),
        :eventId, a, :now, :now
      FROM (SELECT CAST(:account AS jsonb) AS a) incoming
      ON CONFLICT (source_id, company_code, account_number)
      DO UPDATE SET
        account_officer_code = EXCLUDED.account_officer_code,
        import_export_account_number = EXCLUDED.import_export_account_number,
        risk_classification = EXCLUDED.risk_classification,
        internal_classification_code = EXCLUDED.internal_classification_code,
        employee_indicator = EXCLUDED.employee_indicator,
        supplier_indicator = EXCLUDED.supplier_indicator,
        balance_confirmation_date = EXCLUDED.balance_confirmation_date,
        customer_segment_code = EXCLUDED.customer_segment_code,
        corporate_account_number = EXCLUDED.corporate_account_number,
        account_name = EXCLUDED.account_name,
        resident_indicator = EXCLUDED.resident_indicator,
        manager_visibility_level = EXCLUDED.manager_visibility_level,
        activity_code = EXCLUDED.activity_code,
        financial_institution_indicator = EXCLUDED.financial_institution_indicator,
        opened_date = EXCLUDED.opened_date,
        closed_date = EXCLUDED.closed_date,
        hold_correspondence_indicator = EXCLUDED.hold_correspondence_indicator,
        tax_exempt_indicator = EXCLUDED.tax_exempt_indicator,
        pin_number = EXCLUDED.pin_number,
        sector_code = EXCLUDED.sector_code,
        source_event_id = EXCLUDED.source_event_id,
        attributes = EXCLUDED.attributes,
        updated_at = EXCLUDED.updated_at
      RETURNING customer_account_profile_key
      """)
  Mono<UUID> upsertRow(
      @Param("sourceId") String sourceId,
      @Param("eventId") String eventId,
      @Param("account") String account,
      @Param("now") Instant now);
}
