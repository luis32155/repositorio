# Diagrama del customer consumer

El diagrama definitivo corresponde al modelo normalizado de siete tablas del esquema
`cdc_customer`:

```mermaid
erDiagram
    enterprises ||--o{ enterprise_users : "usuarios de empresa"
    users ||--o{ enterprise_users : "perfil"
    enterprise_users ||--o{ user_services : "servicios habilitados"
    services ||--o{ user_services : "catalogo"
    user_services ||--o{ user_service_accounts : "cuentas permitidas"
    accounts ||--o{ user_service_accounts : "catalogo"
    enterprise_users |o--o{ enterprise_users : "actor del evento"
```

La versión detallada, con columnas, reglas y mapeo JSON, está en
[modelo-postgresql-json-7-tablas.md](modelo-postgresql-json-7-tablas.md).
