# Contrato final de tablas

El consumer implementa siete tablas PostgreSQL bajo el esquema `cdc_customer`. El contrato,
las claves, el origen de cada campo y el diagrama ER se encuentran en
[modelo-postgresql-json-7-tablas.md](modelo-postgresql-json-7-tablas.md).

El DDL activo es [schema.sql](../src/main/resources/schema.sql) y la vista materializada está en
[create-customer-profiles-view.sql](../scripts/postgresql/create-customer-profiles-view.sql).
