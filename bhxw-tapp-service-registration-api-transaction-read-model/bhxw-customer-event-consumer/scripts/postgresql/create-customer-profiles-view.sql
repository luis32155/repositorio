-- Optional read view built from the seven CORE-equivalent tables.
DROP MATERIALIZED VIEW IF EXISTS cdc_customer.mv_customer_information_profiles;

CREATE MATERIALIZED VIEW cdc_customer.mv_customer_information_profiles AS
SELECT
    i.customer_identity_key,
    i.source_id,
    i.country_code,
    i.document_type,
    i.document_number,
    i.customer_type,
    i.person_name,
    to_jsonb(p) - 'customer_individual_key' - 'created_at' - 'updated_at'
      AS individual_profile,
    to_jsonb(j) - 'customer_legal_entity_key' - 'created_at' - 'updated_at'
      AS legal_entity_profile,
    COALESCE(
      (
        SELECT jsonb_agg(d.attributes ORDER BY d.contact_type, d.contact_value)
        FROM cdc_customer.customer_digital_contact d
        WHERE d.source_id = i.source_id
          AND d.country_code = i.country_code
          AND d.document_type = i.document_type
          AND d.document_number = i.document_number
      ),
      '[]'::jsonb
    ) AS digital_contacts,
    COALESCE(
      (
        SELECT jsonb_agg(a.attributes ORDER BY a.company_code, a.account_number)
        FROM cdc_customer.customer_account_link l
        JOIN cdc_customer.customer_account_profile a
          ON a.source_id = l.source_id
         AND a.company_code = l.company_code
         AND a.account_number = l.account_number
        WHERE l.source_id = i.source_id
          AND l.country_code = i.country_code
          AND l.document_type = i.document_type
          AND l.document_number = i.document_number
      ),
      '[]'::jsonb
    ) AS account_profiles,
    i.raw_payload,
    i.event_metadata,
    i.source_sequence,
    i.updated_at
FROM cdc_customer.customer_identity i
LEFT JOIN cdc_customer.customer_individual p
  USING (source_id, country_code, document_type, document_number)
LEFT JOIN cdc_customer.customer_legal_entity j
  USING (source_id, country_code, document_type, document_number);

CREATE UNIQUE INDEX ux_mv_customer_information_profiles
  ON cdc_customer.mv_customer_information_profiles (customer_identity_key);
