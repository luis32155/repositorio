\set ON_ERROR_STOP on
REFRESH MATERIALIZED VIEW read_model.mv_customer_core_b28_draft;
DO $$
BEGIN
    IF (SELECT count(*) FROM information_schema.tables
        WHERE table_schema = 'core' AND table_type = 'BASE TABLE') <> 7 THEN
        RAISE EXCEPTION 'Expected exactly 7 CORE base tables';
    END IF;
    IF (SELECT count(*) FROM pg_matviews
        WHERE schemaname = 'read_model' AND matviewname = 'mv_customer_core_b28_draft') <> 1 THEN
        RAISE EXCEPTION 'Expected the B28 materialized view';
    END IF;
    IF (SELECT count(*) FROM read_model.mv_customer_core_b28_draft) <> 3 THEN
        RAISE EXCEPTION 'Expected exactly 3 persons, without contact/account fan-out';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM read_model.mv_customer_core_b28_draft
        WHERE identification_no = 'SYN000000001'
          AND customer_key = '111111111'
          AND email IS NULL AND email_ambiguous
          AND cardinality(verified_emails) = 2
          AND phone = '900000001' AND NOT phone_ambiguous
          AND cardinality(verified_phones) = 1
          AND jsonb_array_length(bt_relationships) = 3
          AND full_name = 'Ana Prueba Datos'
          AND birth_date = DATE '1990-01-02'
    ) THEN
        RAISE EXCEPTION 'Contact grouping, BT grouping or physical profile failed';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM read_model.mv_customer_core_b28_draft
        WHERE identification_no = 'SYN000000002'
          AND customer_key = '222222222'
          AND legal_name = 'Empresa de prueba'
          AND constitution_date_core = DATE '2000-01-01'
          AND full_name IS NULL AND has_legal_details AND NOT has_physical_details
          AND cardinality(verified_emails) = 0 AND contact_source_rows = 0
    ) THEN
        RAISE EXCEPTION 'Legal person without digital contact must remain in the view';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM read_model.mv_customer_core_b28_draft
        WHERE identification_no = 'SYN000000003'
          AND customer_key IS NULL
          AND contact_source_rows = 0 AND country_code IS NULL
          AND bt_relationships = '[]'::jsonb
    ) THEN
        RAISE EXCEPTION 'Unenriched person must remain without fabricated fields';
    END IF;
    RAISE NOTICE 'PASS: 7 CORE tables, materialized view, contacts, BT relations and person subtypes';
END $$;
REFRESH MATERIALIZED VIEW CONCURRENTLY read_model.mv_customer_core_b28_draft;
SELECT count(*) AS persons_after_concurrent_refresh
FROM read_model.mv_customer_core_b28_draft;

