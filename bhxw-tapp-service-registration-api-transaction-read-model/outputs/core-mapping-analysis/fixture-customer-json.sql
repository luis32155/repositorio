\set ON_ERROR_STOP on
INSERT INTO cdc_customer.enterprises
    (source_id, enterprise_sco_id, reference_code, enterprise_name,
     enterprise_country, enterprise_status, constitution_date,
     enterprise_attributes, updated_at)
VALUES
    ('BHXW', '40723', '39800', 'Acme Corporation', 'US',
     'ENTERPRISE_ACTIVE', DATE '2026-12-25',
     '{"enterprise_sco_id":"40723","future_enterprise_field":"preserved"}', now());

INSERT INTO cdc_customer.users
    (user_key, user_full_name, user_middle_name, user_country,
     user_preferred_language, user_email, user_email_domain, phone_number,
     document_type, document_number, person_type, birth_date, marital_status,
     gender, user_attributes, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'Steve Rogers', 'Steve', 'US',
     'en_US', 'pablo@example.com', 'example.com', '999888777', 1,
     '12345678', '12345678', DATE '2026-12-25', '3', '1',
     '{"user_id":"55421","future_user_field":{"enabled":true}}', now(), now()),
    ('00000000-0000-0000-0000-000000000002', 'Steve Rogers', NULL, NULL,
     'en_US', 'Steve@scotiabank.com', 'scotiabank.com', NULL, NULL,
     NULL, NULL, NULL, NULL, NULL,
     '{"user_id":"54901","last_signin_timestamp":"2026-09-21T10:00:00Z"}', now(), now());

INSERT INTO cdc_customer.enterprise_users
    (enterprise_user_key, source_id, enterprise_sco_id, user_key,
     external_user_id, user_role_name, user_status, user_language,
     last_signin_timestamp, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000102', 'BHXW', '40723',
     '00000000-0000-0000-0000-000000000002', '54901', 'Customer', 'ACTIVE',
     'en_US', '2026-09-21T10:00:00Z', now(), now());

INSERT INTO cdc_customer.enterprise_users
    (enterprise_user_key, source_id, enterprise_sco_id, user_key,
     external_user_id, user_role_name, user_status,
     account_transfer_enabled, recipient_maintenance_enabled,
     triggered_by_enterprise_user_key, event_name, snapshot_at, snapshot_order,
     payload_snapshot, event_metadata, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'BHXW', '40723',
     '00000000-0000-0000-0000-000000000001', '55421', 'Administrator', 'ACTIVE',
     true, true, '00000000-0000-0000-0000-000000000102',
     'CUSTOMER_INFORMATION_RETRIEVED', '2026-09-21T10:30:00Z',
     1789986600000000000,
     '{"triggering_user_details":{"user_id":"54901"},"user_details":{"user_id":"55421"},"future_payload":{"code":"X"}}',
     '{"source_id":"BHXW","event_id":"CUSTOMER_INFORMATION_RETRIEVED","future_metadata":true}',
     now(), now());

INSERT INTO cdc_customer.services
    (service_key, source_id, enterprise_sco_id, service_id, service_name,
     service_type, service_attributes, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000201', 'BHXW', '40723', 'ACH001',
     'ACH_PAYABLES', 'ACH', '{"service_id":"ACH001"}', now(), now());

INSERT INTO cdc_customer.accounts
    (account_key, source_id, enterprise_sco_id, account_number, account_type,
     account_name, account_status, currency_code, account_attributes,
     created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000301', 'BHXW', '40723', '123456789',
     'CAA', 'Operating Account', 'A', 'USD',
     '{"account_number":"123456789","future_account_field":1}', now(), now()),
    ('00000000-0000-0000-0000-000000000302', 'BHXW', '40723', '987654321',
     'SBA', 'Savings Account', 'A', 'USD',
     '{"account_number":"987654321"}', now(), now());

INSERT INTO cdc_customer.user_services
    (user_service_key, source_id, enterprise_sco_id, enterprise_user_key,
     service_key, service_selected, user_role, transaction_limit, daily_limit,
     limit_currency, service_order, entitlement_attributes, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000401', 'BHXW', '40723',
     '00000000-0000-0000-0000-000000000101',
     '00000000-0000-0000-0000-000000000201', true, 'CREATOR',
     50000, 500000, 'USD', 0,
     '{"service_id":"ACH001","future_entitlement_field":"preserved"}', now(), now());

INSERT INTO cdc_customer.user_service_accounts
    (source_id, enterprise_sco_id, user_service_key, account_key,
     transaction_limit, daily_limit, account_order, entitlement_attributes,
     created_at, updated_at)
VALUES
    ('BHXW', '40723', '00000000-0000-0000-0000-000000000401',
     '00000000-0000-0000-0000-000000000301', 50000, 500000, 0,
     '{"account_number":"123456789"}', now(), now()),
    ('BHXW', '40723', '00000000-0000-0000-0000-000000000401',
     '00000000-0000-0000-0000-000000000302', 25000, 250000, 1,
     '{"account_number":"987654321"}', now(), now());
