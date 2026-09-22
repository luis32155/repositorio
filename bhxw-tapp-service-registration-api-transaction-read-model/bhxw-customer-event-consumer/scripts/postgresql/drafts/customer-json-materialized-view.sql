-- Aplicar primero customer-json-7-table-schema.sql.
-- Perfil de lectura derivado de las siete tablas normalizadas del JSON.

CREATE MATERIALIZED VIEW IF NOT EXISTS
    cdc_customer.mv_customer_information_profiles AS
SELECT
    eu.enterprise_user_key,
    eu.source_id,
    eu.enterprise_sco_id,
    eu.external_user_id AS user_id,
    u.user_full_name,
    u.user_middle_name,
    u.user_country,
    u.user_preferred_language,
    u.user_email,
    u.user_email_domain,
    u.phone_number,
    u.document_type,
    u.document_number,
    u.person_type,
    u.birth_date,
    u.marital_status,
    u.gender,
    eu.user_role_name,
    eu.user_status,
    eu.account_transfer_enabled,
    eu.recipient_maintenance_enabled,
    e.reference_code,
    e.enterprise_name,
    e.enterprise_country,
    e.enterprise_status,
    e.constitution_date,
    actor.external_user_id AS triggering_user_id,
    eu.payload_snapshot,
    eu.event_metadata,
    COALESCE(entitlements.service_count, 0) AS service_count,
    COALESCE(entitlements.account_count, 0) AS account_count,
    COALESCE(entitlements.services, '[]'::jsonb) AS services,
    eu.snapshot_at,
    eu.updated_at
FROM cdc_customer.enterprise_users eu
JOIN cdc_customer.users u
  ON u.user_key = eu.user_key
JOIN cdc_customer.enterprises e
  ON e.source_id = eu.source_id
 AND e.enterprise_sco_id = eu.enterprise_sco_id
LEFT JOIN cdc_customer.enterprise_users actor
  ON actor.enterprise_user_key = eu.triggered_by_enterprise_user_key
LEFT JOIN LATERAL (
    SELECT
        count(*) AS service_count,
        COALESCE(sum(service_accounts.account_count), 0) AS account_count,
        jsonb_agg(
            jsonb_build_object(
                'service_id', s.service_id,
                'service_name', s.service_name,
                'service_type', s.service_type,
                'service_selected', us.service_selected,
                'user_role', us.user_role,
                'service_details', jsonb_build_object(
                    'transaction_limit', us.transaction_limit,
                    'daily_limit', us.daily_limit,
                    'limit_currency', us.limit_currency
                ),
                'accounts', COALESCE(service_accounts.accounts, '[]'::jsonb)
            ) ORDER BY us.service_order
        ) AS services
    FROM cdc_customer.user_services us
    JOIN cdc_customer.services s
      ON s.service_key = us.service_key
    LEFT JOIN LATERAL (
        SELECT
            count(*) AS account_count,
            jsonb_agg(
                jsonb_build_object(
                    'account_number', a.account_number,
                    'account_type', a.account_type,
                    'account_name', a.account_name,
                    'account_status', a.account_status,
                    'transaction_limit', usa.transaction_limit,
                    'daily_limit', usa.daily_limit,
                    'currency_code', a.currency_code
                ) ORDER BY usa.account_order
            ) AS accounts
        FROM cdc_customer.user_service_accounts usa
        JOIN cdc_customer.accounts a
          ON a.account_key = usa.account_key
        WHERE usa.user_service_key = us.user_service_key
    ) service_accounts ON true
    WHERE us.enterprise_user_key = eu.enterprise_user_key
) entitlements ON true
WHERE eu.payload_snapshot IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mv_customer_information_profiles
    ON cdc_customer.mv_customer_information_profiles (enterprise_user_key);

-- REFRESH MATERIALIZED VIEW
--   cdc_customer.mv_customer_information_profiles;
-- REFRESH MATERIALIZED VIEW CONCURRENTLY
--   cdc_customer.mv_customer_information_profiles;
