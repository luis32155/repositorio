# bhxw-customer-event-consumer

Consumer reactivo del evento `CUSTOMER_INFORMATION_RETRIEVED`, construido con Java 25,
Spring Boot 4, Reactor, Kafka y PostgreSQL/R2DBC. Consume un solo tópico configurable,
`bhxw.tapp.customer.cdc.v1`, y proyecta cada snapshot completo en siete tablas del esquema
`cdc_customer`.

## Flujo

```text
Kafka -> deserialización dinámica -> validación -> mapeo -> transacción R2DBC
      -> siete tablas PostgreSQL -> finalización del Mono -> confirmación del offset
```

No se usa JDBC, JPA ni `block()` en código de producción. El listener devuelve `Mono<Void>`,
la escritura completa ocurre dentro de una transacción reactiva y Kafka confirma el registro
únicamente después del commit.

Virtual threads están habilitados para los ejecutores compatibles de Spring mediante
`VIRTUAL_THREADS_ENABLED=true`. Reactor y R2DBC mantienen el camino principal no bloqueante;
los virtual threads no sustituyen sus event loops.

## Modelo de siete tablas

| # | Tabla | Responsabilidad |
|---:|---|---|
| 1 | `cdc_customer.enterprises` | Empresas del actor y del usuario objetivo |
| 2 | `cdc_customer.users` | Perfil personal y atributos dinámicos |
| 3 | `cdc_customer.enterprise_users` | Identidad, rol y estado del usuario dentro de la empresa |
| 4 | `cdc_customer.services` | Catálogo de servicios observado |
| 5 | `cdc_customer.accounts` | Catálogo de cuentas observado |
| 6 | `cdc_customer.user_services` | Permisos y límites por usuario y servicio |
| 7 | `cdc_customer.user_service_accounts` | Límites por usuario, servicio y cuenta |

El actor de `payload.triggering_user_details` y el usuario objetivo de
`payload.user_details` se guardan como identidades independientes. El actor es una observación
parcial: se actualizan solamente sus campos presentes y nunca se reemplazan sus permisos.
El estado completo y los permisos corresponden al usuario objetivo.

El DDL ejecutado por la aplicación está en
[`src/main/resources/schema.sql`](src/main/resources/schema.sql). El diagrama y el mapeo se
encuentran en [`docs/modelo-postgresql-json-7-tablas.md`](docs/modelo-postgresql-json-7-tablas.md).

## Payload dinámico

El envelope se deserializa como árboles JSON. Los campos conocidos se normalizan y cada nivel
conserva su contenido en `JSONB`: empresa, usuario, servicio, cuenta y relaciones de permisos.
Además, `enterprise_users.payload_snapshot` y `event_metadata` conservan el evento completo.

Se validan las claves estructurales necesarias para identificar y aplicar el snapshot:

- `metadata.source_id`, `metadata.event_id` y `metadata.event_timestamp`.
- Los identificadores de actor, usuario objetivo y sus empresas.
- `user_details.entitlements.services` como arreglo completo.
- `service_id`, `accounts` y `account_number`.
- Fechas, timestamps, booleanos y límites numéricos que afectan la persistencia.

Los campos desconocidos pueden aparecer a cualquier profundidad y no se descartan. Como el
producer entrega el estado completo, `services: []` revoca todos los servicios del usuario y
`accounts: []` revoca las cuentas del servicio. Omitir esos arreglos se rechaza para evitar
borrados por mensajes incompletos.

## Separación de clases

- `CustomerEventConsumer`: adaptación Kafka, reintentos y observabilidad.
- `CustomerEventValidator`: reglas del contrato estable y extensibilidad dinámica.
- `CustomerSnapshotMapper`: transformación pura del evento validado al dominio.
- `CustomerEventProjectionService`: composición funcional del caso de uso.
- `PostgresCustomerProfileRepositoryAdapter`: coordinación transaccional e idempotencia.
- `EnterpriseTableRepository`, `UserTableRepository`, `EnterpriseUserTableRepository`,
  `ServiceTableRepository`, `AccountTableRepository`, `UserServiceTableRepository` y
  `UserServiceAccountTableRepository`: interfaces `ReactiveCrudRepository` con SQL aislado por
  tabla mediante `@Query`.
- `PostgresAdvisoryLockRepository`: único componente que usa `DatabaseClient`, exclusivamente
  para el advisory lock transaccional que no corresponde a una entidad CRUD.

Los puertos de entrada y salida permanecen como interfaces funcionales. La composición usa
`map`, `flatMap`, `concatMap`, `switchIfEmpty` y operadores transaccionales de Reactor.

## Orden, concurrencia e idempotencia

La identidad de una proyección es `source_id + enterprise_sco_id + user_id`. Dentro de la
transacción se adquieren advisory locks de PostgreSQL para el actor y el objetivo, en orden
determinista, evitando carreras de inserción y deadlocks entre eventos relacionados.

La revisión provisional usa `user_details.event_timestamp`; cuando no existe, usa
`metadata.event_timestamp`:

- Misma fecha y mismo payload JSONB: `IGNORED`.
- Fecha anterior: `IGNORED`.
- Fecha posterior: `APPLIED`.
- Misma fecha y payload diferente: error de conflicto.

Un evento aplicado actualiza identidades y catálogos, elimina solamente las relaciones de
permisos del usuario objetivo y las reconstruye desde el snapshot. Si falla cualquier operación,
PostgreSQL revierte las siete tablas.

## Kafka y errores

- Un solo tópico y un registro en vuelo por consumer.
- Tres reintentos reactivos para errores de persistencia.
- Payload inválido, conflicto o reintentos agotados detienen el listener sin confirmar el offset.
- Los logs no incluyen payloads, parámetros SQL ni datos personales.
- La entrega es al menos una vez; Kafka y PostgreSQL no comparten una transacción distribuida.

`event_id` representa el nombre del evento y no se trata como identificador único. La clave
Kafka propuesta es `enterprise_sco_id|user_id`; debe confirmarse con el producer.

## Vista materializada

[`scripts/postgresql/create-customer-profiles-view.sql`](scripts/postgresql/create-customer-profiles-view.sql)
crea `cdc_customer.mv_customer_information_profiles` a partir de las siete tablas.

```sql
REFRESH MATERIALIZED VIEW CONCURRENTLY
    cdc_customer.mv_customer_information_profiles;
```

La periodicidad del refresh debe configurarse según el SLA de lectura.

## Configuración

| Variable | Uso |
|---|---|
| `POSTGRES_R2DBC_URL` | URL reactiva de PostgreSQL |
| `POSTGRES_USER` | Usuario de base de datos |
| `POSTGRES_PASSWORD` | Contraseña desde secretos |
| `DB_INIT_MODE` | `never` por defecto; `always` sólo para inicialización controlada |
| `KAFKA_BOOTSTRAP_SERVERS` | Brokers Kafka |
| `KAFKA_TOPIC_CUSTOMER_EVENTS` | Único tópico de entrada |
| `KAFKA_CONSUMER_GROUP` | Grupo del consumer |
| `KAFKA_CONCURRENCY` | Concurrencia según particiones |
| `VIRTUAL_THREADS_ENABLED` | Virtual threads de Spring, `true` por defecto |

## Pruebas

```powershell
$env:CUSTOMER_TEST_R2DBC_URL = 'r2dbc:postgresql://customer_test@127.0.0.1:55432/customer_dynamic_test'
.\gradlew.bat clean check bootJar
```

Con la variable definida se prueban PostgreSQL real y Kafka embebido: siete tablas, JSON
dinámico, actor y objetivo, idempotencia, snapshots antiguos, conflictos, revocaciones,
rollback, concurrencia, offsets y virtual threads. Sin ella se omiten las pruebas que requieren
PostgreSQL.
