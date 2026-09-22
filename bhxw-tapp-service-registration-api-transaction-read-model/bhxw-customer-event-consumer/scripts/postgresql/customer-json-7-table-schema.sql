-- PostgreSQL read model: semantic replica of the seven CORE structures.
CREATE SCHEMA IF NOT EXISTS cdc_customer;

CREATE TABLE IF NOT EXISTS cdc_customer.customer_identity (
    customer_identity_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    country_code NUMERIC(3, 0) NOT NULL,
    document_type NUMERIC(2, 0) NOT NULL,
    document_number VARCHAR(12) NOT NULL,
    customer_type CHAR(1) NOT NULL,
    person_name VARCHAR(30) NOT NULL,
    source_event_id TEXT NOT NULL,
    source_sequence NUMERIC(30, 0) NOT NULL,
    raw_payload JSONB NOT NULL CHECK (jsonb_typeof(raw_payload) = 'object'),
    event_metadata JSONB NOT NULL CHECK (jsonb_typeof(event_metadata) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customer_identity
      UNIQUE (source_id, country_code, document_type, document_number)
);

CREATE TABLE IF NOT EXISTS cdc_customer.customer_individual (
    customer_individual_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    country_code NUMERIC(3, 0) NOT NULL,
    document_type NUMERIC(2, 0) NOT NULL,
    document_number VARCHAR(12) NOT NULL,
    first_last_name VARCHAR(30),
    second_last_name VARCHAR(30),
    first_given_name VARCHAR(25),
    second_given_name VARCHAR(25),
    birth_date DATE,
    marital_status CHAR(1),
    gender CHAR(1),
    attributes JSONB NOT NULL CHECK (jsonb_typeof(attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customer_individual
      UNIQUE (source_id, country_code, document_type, document_number),
    CONSTRAINT fk_customer_individual_identity
      FOREIGN KEY (source_id, country_code, document_type, document_number)
      REFERENCES cdc_customer.customer_identity
        (source_id, country_code, document_type, document_number)
      ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cdc_customer.customer_legal_entity (
    customer_legal_entity_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    country_code NUMERIC(3, 0) NOT NULL,
    document_type NUMERIC(2, 0) NOT NULL,
    document_number VARCHAR(12) NOT NULL,
    legal_name VARCHAR(70) NOT NULL,
    constitution_date DATE,
    attributes JSONB NOT NULL CHECK (jsonb_typeof(attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customer_legal_entity
      UNIQUE (source_id, country_code, document_type, document_number),
    CONSTRAINT fk_customer_legal_entity_identity
      FOREIGN KEY (source_id, country_code, document_type, document_number)
      REFERENCES cdc_customer.customer_identity
        (source_id, country_code, document_type, document_number)
      ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS cdc_customer.customer_digital_contact (
    customer_digital_contact_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    country_code NUMERIC(3, 0) NOT NULL,
    document_type NUMERIC(2, 0) NOT NULL,
    document_number VARCHAR(12) NOT NULL,
    contact_type CHAR(1) NOT NULL,
    contact_value VARCHAR(70) NOT NULL,
    validation_status CHAR(1) NOT NULL,
    attributes JSONB NOT NULL CHECK (jsonb_typeof(attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customer_digital_contact
      UNIQUE (source_id, country_code, document_type, document_number,
              contact_type, contact_value),
    CONSTRAINT fk_customer_digital_contact_identity
      FOREIGN KEY (source_id, country_code, document_type, document_number)
      REFERENCES cdc_customer.customer_identity
        (source_id, country_code, document_type, document_number)
      ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ck_customer_digital_contact_type CHECK (contact_type IN ('1', '2')),
    CONSTRAINT ck_customer_digital_contact_status CHECK (validation_status IN ('0', '1'))
);

CREATE TABLE IF NOT EXISTS cdc_customer.customer_account_profile (
    customer_account_profile_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    company_code NUMERIC(3, 0) NOT NULL,
    account_number NUMERIC(9, 0) NOT NULL,
    account_officer_code NUMERIC(3, 0),
    import_export_account_number NUMERIC(9, 0),
    risk_classification NUMERIC(1, 0),
    internal_classification_code NUMERIC(4, 0),
    employee_indicator CHAR(1),
    supplier_indicator CHAR(1),
    balance_confirmation_date DATE,
    customer_segment_code NUMERIC(2, 0),
    corporate_account_number NUMERIC(9, 0),
    account_name VARCHAR(35),
    resident_indicator CHAR(1),
    manager_visibility_level NUMERIC(2, 0),
    activity_code NUMERIC(3, 0),
    financial_institution_indicator CHAR(1),
    opened_date DATE,
    closed_date DATE,
    hold_correspondence_indicator CHAR(1),
    tax_exempt_indicator CHAR(1),
    pin_number NUMERIC(9, 0),
    sector_code NUMERIC(3, 0),
    source_event_id TEXT NOT NULL,
    attributes JSONB NOT NULL CHECK (jsonb_typeof(attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customer_account_profile UNIQUE (source_id, company_code, account_number)
);

CREATE TABLE IF NOT EXISTS cdc_customer.customer_account_link (
    customer_account_link_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    company_code NUMERIC(3, 0) NOT NULL,
    account_number NUMERIC(9, 0) NOT NULL,
    country_code NUMERIC(3, 0) NOT NULL,
    document_type NUMERIC(2, 0) NOT NULL,
    document_number VARCHAR(12) NOT NULL,
    attributes JSONB NOT NULL CHECK (jsonb_typeof(attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customer_account_link
      UNIQUE (source_id, company_code, account_number,
              country_code, document_type, document_number),
    CONSTRAINT fk_customer_account_link_identity
      FOREIGN KEY (source_id, country_code, document_type, document_number)
      REFERENCES cdc_customer.customer_identity
        (source_id, country_code, document_type, document_number)
      ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_customer_account_link_account
      FOREIGN KEY (source_id, company_code, account_number)
      REFERENCES cdc_customer.customer_account_profile
        (source_id, company_code, account_number)
      ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS cdc_customer.customer_account_consolidation (
    customer_account_consolidation_key UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id TEXT NOT NULL,
    relationship_code NUMERIC(3, 0) NOT NULL,
    integrated_company_code NUMERIC(3, 0) NOT NULL,
    integrated_account_number NUMERIC(9, 0) NOT NULL,
    source_company_code NUMERIC(3, 0) NOT NULL,
    source_account_number NUMERIC(9, 0) NOT NULL,
    attributes JSONB NOT NULL CHECK (jsonb_typeof(attributes) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_customer_account_consolidation
      UNIQUE (source_id, relationship_code, integrated_company_code,
              integrated_account_number, source_company_code, source_account_number),
    CONSTRAINT fk_consolidation_integrated_account
      FOREIGN KEY (source_id, integrated_company_code, integrated_account_number)
      REFERENCES cdc_customer.customer_account_profile
        (source_id, company_code, account_number)
      ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_consolidation_source_account
      FOREIGN KEY (source_id, source_company_code, source_account_number)
      REFERENCES cdc_customer.customer_account_profile
        (source_id, company_code, account_number)
      ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_identity_document
  ON cdc_customer.customer_identity (country_code, document_type, document_number);
CREATE INDEX IF NOT EXISTS idx_customer_account_link_document
  ON cdc_customer.customer_account_link (source_id, country_code, document_type, document_number);
CREATE INDEX IF NOT EXISTS idx_customer_account_link_account
  ON cdc_customer.customer_account_link (source_id, company_code, account_number);
CREATE INDEX IF NOT EXISTS idx_customer_consolidation_integrated
  ON cdc_customer.customer_account_consolidation
    (source_id, integrated_company_code, integrated_account_number);
