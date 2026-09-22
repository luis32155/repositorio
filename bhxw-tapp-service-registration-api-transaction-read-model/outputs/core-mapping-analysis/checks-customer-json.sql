\set ON_ERROR_STOP on
REFRESH MATERIALIZED VIEW
    cdc_customer.mv_customer_information_profiles;

DO $$
BEGIN
    IF (SELECT count(*) FROM information_schema.tables
        WHERE table_schema = 'cdc_customer'
          AND table_type = 'BASE TABLE') <> 7 THEN
        RAISE EXCEPTION 'Expected exactly 7 JSON domain tables';
    END IF;
    IF (SELECT count(*) FROM pg_matviews
        WHERE schemaname = 'cdc_customer'
          AND matviewname = 'mv_customer_information_profiles') <> 1 THEN
        RAISE EXCEPTION 'Expected the customer information materialized view';
    END IF;
    IF (SELECT count(*) FROM cdc_customer.mv_customer_information_profiles) <> 1 THEN
        RAISE EXCEPTION 'Expected only the target user profile';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM cdc_customer.mv_customer_information_profiles
        WHERE source_id = 'BHXW'
          AND enterprise_sco_id = '40723'
          AND user_id = '55421'
          AND triggering_user_id = '54901'
          AND enterprise_name = 'Acme Corporation'
          AND person_type = '12345678'
          AND birth_date = DATE '2026-12-25'
          AND service_count = 1
          AND account_count = 2
          AND services #>> '{0,service_id}' = 'ACH001'
          AND services #>> '{0,accounts,0,account_number}' = '123456789'
          AND services #>> '{0,accounts,1,account_number}' = '987654321'
          AND payload_snapshot #>> '{future_payload,code}' = 'X'
          AND event_metadata @> '{"future_metadata":true}'::jsonb
    ) THEN
        RAISE EXCEPTION 'Materialized profile does not match the JSON domain model';
    END IF;
    RAISE NOTICE 'PASS: 7 JSON tables, actor, target, service, accounts and materialized view';
END $$;

REFRESH MATERIALIZED VIEW CONCURRENTLY
    cdc_customer.mv_customer_information_profiles;
