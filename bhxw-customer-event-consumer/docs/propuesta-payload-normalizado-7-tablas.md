# Payload normalizado en siete tablas

La propuesta ya fue incorporada al consumer. La implementación definitiva usa el esquema
`cdc_customer`, programación reactiva con R2DBC y siete repositorios de tabla separados.

Consulta el [modelo PostgreSQL definitivo](modelo-postgresql-json-7-tablas.md), el
[DDL activo](../src/main/resources/schema.sql) y la
[vista materializada](../scripts/postgresql/create-customer-profiles-view.sql).
