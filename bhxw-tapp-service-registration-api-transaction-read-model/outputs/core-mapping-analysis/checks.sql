\set ON_ERROR_STOP on
REFRESH MATERIALIZED VIEW read_model.mv_customer_core_b28_draft;
DO $$
BEGIN
    IF (SELECT count(*) FROM read_model.mv_customer_core_b28_draft) <> 3 THEN
        RAISE EXCEPTION 'Expected exactly 3 persons, without contact/account fan-out';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM read_model.mv_customer_core_b28_draft
        WHERE identification_no='SYN000000001'
          AND email IS NULL AND email_ambiguous
          AND cardinality(verified_emails)=2
          AND phone='900000001' AND NOT phone_ambiguous
          AND cardinality(verified_phones)=1
          AND jsonb_array_length(bt_relationships)=3
          AND full_name='Ana Prueba Datos'
          AND customer_key IS NULL AND customer_type IS NULL
          AND birth_date IS NULL AND birth_date_core='19900102'
    ) THEN RAISE EXCEPTION 'Contact ambiguity, filtering, BT grouping or pending-field handling failed'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM read_model.mv_customer_core_b28_draft
        WHERE identification_no='SYN000000002' AND legal_name='Empresa de prueba'
          AND full_name IS NULL AND has_legal_details AND NOT has_physical_details
          AND cardinality(verified_emails)=0 AND contact_source_rows=0
    ) THEN RAISE EXCEPTION 'Legal person without digital contact must remain in draft'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM read_model.mv_customer_core_b28_draft
        WHERE identification_no='SYN000000003' AND contact_source_rows=0
          AND country_code IS NULL AND core_document_country=604
          AND bt_relationships='[]'::jsonb
    ) THEN RAISE EXCEPTION 'Unenriched person must remain visible without fabricated fields'; END IF;
    BEGIN
        INSERT INTO core.fsd002 SELECT * FROM core.fsd002;
        REFRESH MATERIALIZED VIEW read_model.mv_customer_core_b28_draft;
        RAISE EXCEPTION 'Expected unique constraint failure for duplicated person subtype';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;
    RAISE NOTICE 'PASS: person grain, multiple contacts, validation state, BT associations, pending fields, legal subtype, missing enrichment, duplicate detection';
END $$;
REFRESH MATERIALIZED VIEW CONCURRENTLY read_model.mv_customer_core_b28_draft;
SELECT count(*) AS persons_after_concurrent_refresh FROM read_model.mv_customer_core_b28_draft;
ROLLBACK;
