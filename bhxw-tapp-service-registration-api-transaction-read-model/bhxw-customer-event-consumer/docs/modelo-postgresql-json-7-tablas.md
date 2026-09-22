# Modelo PostgreSQL de siete tablas

```mermaid
erDiagram
    CUSTOMER_IDENTITY ||--o| CUSTOMER_INDIVIDUAL : "puede ser"
    CUSTOMER_IDENTITY ||--o| CUSTOMER_LEGAL_ENTITY : "puede ser"
    CUSTOMER_IDENTITY ||--o{ CUSTOMER_DIGITAL_CONTACT : "posee"
    CUSTOMER_IDENTITY ||--o{ CUSTOMER_ACCOUNT_LINK : "se vincula"
    CUSTOMER_ACCOUNT_PROFILE ||--o{ CUSTOMER_ACCOUNT_LINK : "identifica cuenta"
    CUSTOMER_ACCOUNT_PROFILE ||--o{ CUSTOMER_ACCOUNT_CONSOLIDATION : "cuenta origen"
    CUSTOMER_ACCOUNT_PROFILE ||--o{ CUSTOMER_ACCOUNT_CONSOLIDATION : "cuenta integrada"

    CUSTOMER_IDENTITY {
      uuid customer_identity_key PK
      numeric country_code UK
      numeric document_type UK
      varchar document_number UK
      char customer_type
      varchar person_name
      numeric source_sequence
      jsonb raw_payload
    }
    CUSTOMER_INDIVIDUAL {
      uuid customer_individual_key PK
      varchar first_last_name
      varchar first_given_name
      date birth_date
    }
    CUSTOMER_LEGAL_ENTITY {
      uuid customer_legal_entity_key PK
      varchar legal_name
      date constitution_date
    }
    CUSTOMER_DIGITAL_CONTACT {
      uuid customer_digital_contact_key PK
      char contact_type
      varchar contact_value
      char validation_status
    }
    CUSTOMER_ACCOUNT_PROFILE {
      uuid customer_account_profile_key PK
      numeric company_code UK
      numeric account_number UK
      varchar account_name
      jsonb attributes
    }
    CUSTOMER_ACCOUNT_LINK {
      uuid customer_account_link_key PK
      numeric company_code FK
      numeric account_number FK
      numeric country_code FK
      numeric document_type FK
      varchar document_number FK
    }
    CUSTOMER_ACCOUNT_CONSOLIDATION {
      uuid customer_account_consolidation_key PK
      numeric relationship_code
      numeric integrated_company_code FK
      numeric integrated_account_number FK
      numeric source_company_code FK
      numeric source_account_number FK
    }
```

## Relación con CORE

| CORE | PostgreSQL | Sección JSON |
|---|---|---|
| FSD001 | `customer_identity` | `customer_identity` |
| FSD002 | `customer_individual` | `individual_profile` |
| FSD003 | `customer_legal_entity` | `legal_entity_profile` |
| LWVD50A | `customer_digital_contact` | `digital_contacts[]` |
| FSD008 | `customer_account_profile` | `account_profiles[]` |
| FSR008 | `customer_account_link` | `customer_account_links[]` |
| LCPD18 | `customer_account_consolidation` | `account_consolidations[]` |

Las claves UUID son técnicas para Spring Data. La equivalencia funcional se protege con
restricciones `UNIQUE` compuestas y claves foráneas sobre país, tipo y número de documento
o sobre empresa y número de cuenta.

Cada sección conserva campos adicionales en `JSONB`. La tabla de identidad también
conserva el payload y metadata completos.
