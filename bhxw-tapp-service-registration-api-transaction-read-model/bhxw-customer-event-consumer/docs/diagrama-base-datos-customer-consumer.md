# Diagrama del consumer de cliente

El diagrama, las relaciones y el mapeo CORE están consolidados en
[`modelo-postgresql-json-7-tablas.md`](modelo-postgresql-json-7-tablas.md).

El consumer recibe un snapshot `FULL` desde un solo tópico y reemplaza sus relaciones
en una única transacción reactiva.
