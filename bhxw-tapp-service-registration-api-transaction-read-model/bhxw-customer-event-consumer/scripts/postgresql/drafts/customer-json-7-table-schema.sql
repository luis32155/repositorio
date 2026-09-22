-- PostgreSQL 18 - modelo del consumer para el JSON CUSTOMER_INFORMATION.
-- Siete tablas de dominio. No replica los nombres fisicos de las tablas CORE.

CREATE SCHEMA IF NOT EXISTS cdc_customer;

-- 1. payload.user_details.enterprise_details y empresa del triggering user.
CREATE TABLE IF NOT EXISTS cdc_customer.enterprises (
    enterprise_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    enterprise_sco_id TEXT NOT NULL,
    reference_code TEXT,
    enterprise_name TEXT,
    enterprise_country TEXT,
    enterprise_status TEXT,
    constitution_date DATE,
    enterprise_attributes JSONB NOT NULL
        CHECK (jsonb_typeof(enterprise_attributes) = 'object'),
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (source_id, enterprise_sco_id)
);

-- 2. Perfil de una persona/usuario. No se fuerza unicidad por email o documento.
CREATE TABLE IF NOT EXISTS cdc_customer.users (
    user_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_full_name TEXT,
    user_middle_name TEXT,
    user_country TEXT,
    user_preferred_language TEXT,
    user_email TEXT,
    user_email_domain TEXT,
    phone_number TEXT,
    document_type INTEGER,
    document_number TEXT,
    person_type TEXT,
    birth_date DATE,
    marital_status TEXT,
    gender TEXT,
    user_attributes JSONB NOT NULL
        CHECK (jsonb_typeof(user_attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_users_document
    ON cdc_customer.users (document_type, document_number);

-- 3. Identidad del usuario dentro de una empresa.
-- Aquí viven user_id, rol, estado, permisos generales y el snapshot completo.
CREATE TABLE IF NOT EXISTS cdc_customer.enterprise_users (
    enterprise_user_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    enterprise_sco_id TEXT NOT NULL,
    user_key UUID NOT NULL,
    external_user_id TEXT NOT NULL,
    user_role_name TEXT,
    user_status TEXT,
    user_language TEXT,
    last_signin_timestamp TIMESTAMPTZ,
    account_transfer_enabled BOOLEAN,
    recipient_maintenance_enabled BOOLEAN,
    triggered_by_enterprise_user_key UUID,
    event_name TEXT,
    snapshot_at TIMESTAMPTZ,
    snapshot_order NUMERIC(30, 0),
    payload_snapshot JSONB
        CHECK (payload_snapshot IS NULL OR jsonb_typeof(payload_snapshot) = 'object'),
    event_metadata JSONB
        CHECK (event_metadata IS NULL OR jsonb_typeof(event_metadata) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (source_id, enterprise_sco_id, external_user_id),
    UNIQUE (source_id, enterprise_sco_id, user_key),
    UNIQUE (source_id, enterprise_sco_id, enterprise_user_key),
    CONSTRAINT fk_enterprise_users_enterprise
      FOREIGN KEY (source_id, enterprise_sco_id)
      REFERENCES cdc_customer.enterprises (source_id, enterprise_sco_id)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_enterprise_users_user
      FOREIGN KEY (user_key)
      REFERENCES cdc_customer.users (user_key)
      ON DELETE RESTRICT,
    CONSTRAINT fk_enterprise_users_actor
      FOREIGN KEY (triggered_by_enterprise_user_key)
      REFERENCES cdc_customer.enterprise_users (enterprise_user_key)
      DEFERRABLE INITIALLY DEFERRED
);

-- 4. Catálogo de servicios observado dentro de la empresa.
CREATE TABLE IF NOT EXISTS cdc_customer.services (
    service_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    enterprise_sco_id TEXT NOT NULL,
    service_id TEXT NOT NULL,
    service_name TEXT,
    service_type TEXT,
    service_attributes JSONB NOT NULL
        CHECK (jsonb_typeof(service_attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (source_id, enterprise_sco_id, service_id),
    UNIQUE (source_id, enterprise_sco_id, service_key),
    CONSTRAINT fk_services_enterprise
      FOREIGN KEY (source_id, enterprise_sco_id)
      REFERENCES cdc_customer.enterprises (source_id, enterprise_sco_id)
      ON UPDATE CASCADE ON DELETE RESTRICT
);

-- 5. Catálogo de cuentas observado dentro de la empresa.
CREATE TABLE IF NOT EXISTS cdc_customer.accounts (
    account_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    enterprise_sco_id TEXT NOT NULL,
    account_number TEXT NOT NULL,
    account_type TEXT,
    account_name TEXT,
    account_status TEXT,
    currency_code TEXT,
    account_attributes JSONB NOT NULL
        CHECK (jsonb_typeof(account_attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (source_id, enterprise_sco_id, account_number),
    UNIQUE (source_id, enterprise_sco_id, account_key),
    CONSTRAINT fk_accounts_enterprise
      FOREIGN KEY (source_id, enterprise_sco_id)
      REFERENCES cdc_customer.enterprises (source_id, enterprise_sco_id)
      ON UPDATE CASCADE ON DELETE RESTRICT
);

-- 6. payload.user_details.entitlements.services[] para el usuario objetivo.
CREATE TABLE IF NOT EXISTS cdc_customer.user_services (
    user_service_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    enterprise_sco_id TEXT NOT NULL,
    enterprise_user_key UUID NOT NULL,
    service_key UUID NOT NULL,
    service_selected BOOLEAN,
    user_role TEXT,
    transaction_limit NUMERIC CHECK (transaction_limit >= 0),
    daily_limit NUMERIC CHECK (daily_limit >= 0),
    limit_currency TEXT,
    service_order INTEGER NOT NULL CHECK (service_order >= 0),
    entitlement_attributes JSONB NOT NULL
        CHECK (jsonb_typeof(entitlement_attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (enterprise_user_key, service_key),
    UNIQUE (source_id, enterprise_sco_id, user_service_key),
    CONSTRAINT fk_user_services_enterprise_user
      FOREIGN KEY (source_id, enterprise_sco_id, enterprise_user_key)
      REFERENCES cdc_customer.enterprise_users
        (source_id, enterprise_sco_id, enterprise_user_key)
      ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_user_services_service
      FOREIGN KEY (source_id, enterprise_sco_id, service_key)
      REFERENCES cdc_customer.services
        (source_id, enterprise_sco_id, service_key)
      ON UPDATE CASCADE ON DELETE RESTRICT
);

-- 7. services[].accounts[]: permisos y límites para usuario + servicio + cuenta.
CREATE TABLE IF NOT EXISTS cdc_customer.user_service_accounts (
    user_service_account_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    enterprise_sco_id TEXT NOT NULL,
    user_service_key UUID NOT NULL,
    account_key UUID NOT NULL,
    transaction_limit NUMERIC CHECK (transaction_limit >= 0),
    daily_limit NUMERIC CHECK (daily_limit >= 0),
    account_order INTEGER NOT NULL CHECK (account_order >= 0),
    entitlement_attributes JSONB NOT NULL
        CHECK (jsonb_typeof(entitlement_attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (source_id, enterprise_sco_id, user_service_key, account_key),
    CONSTRAINT fk_user_service_accounts_user_service
      FOREIGN KEY (source_id, enterprise_sco_id, user_service_key)
      REFERENCES cdc_customer.user_services
        (source_id, enterprise_sco_id, user_service_key)
      ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_user_service_accounts_account
      FOREIGN KEY (source_id, enterprise_sco_id, account_key)
      REFERENCES cdc_customer.accounts
        (source_id, enterprise_sco_id, account_key)
      ON UPDATE CASCADE ON DELETE RESTRICT
);
