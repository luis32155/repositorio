# bhxw-customer-event-consumer

Consumer reactivo de un snapshot completo de cliente. Usa Java 25, Spring Boot 4,
Kafka, Reactor, R2DBC y PostgreSQL. Un pod consume un solo tópico configurable:
`bhxw.tapp.customer.cdc.v1`.

## Flujo

```text
Kafka -> JSON dinámico -> validación -> mapeo -> transacción R2DBC
      -> siete tablas cdc_customer -> commit -> confirmación del offset Kafka
```

El listener devuelve `Mono<Void>`. El código de producción no usa JPA, JDBC ni
`block()`. Las siete interfaces de tabla extienden `ReactiveCrudRepository`; el
`DatabaseClient` se usa únicamente para el advisory lock transaccional.

## Siete tablas PostgreSQL

| Tabla PostgreSQL | Equivalente CORE | Contenido |
|---|---|---|
| `customer_identity` | FSD001 | Identidad base del cliente |
| `customer_individual` | FSD002 | Datos de persona natural |
| `customer_legal_entity` | FSD003 | Datos de persona jurídica |
| `customer_digital_contact` | LWVD50A | Correos y celulares |
| `customer_account_profile` | FSD008 | Datos maestros de las cuentas |
| `customer_account_link` | FSR008 | Relación documento-cuenta |
| `customer_account_consolidation` | LCPD18 | Relación entre cuenta origen e integrada |

El DDL activo está en
[`src/main/resources/schema.sql`](src/main/resources/schema.sql). Conserva las
relaciones mediante claves foráneas y agrega una clave UUID técnica a cada tabla para
trabajar con `ReactiveCrudRepository`. Los identificadores funcionales siguen siendo
únicos mediante restricciones compuestas.

## Contrato dinámico

El evento contiene `payload.snapshot_mode: FULL` y estas siete secciones:

- `customer_identity`
- `individual_profile`
- `legal_entity_profile`
- `digital_contacts[]`
- `account_profiles[]`
- `customer_account_links[]`
- `account_consolidations[]`

El ejemplo completo está en
[`docs/customer-information-event.json`](docs/customer-information-event.json).
Los campos necesarios para relacionar filas se normalizan. El payload completo,
metadata y atributos de cada sección se conservan en columnas `JSONB`, por lo que el
producer puede agregar campos sin romper el consumer.

La clave Kafka recomendada es
`country_code|document_type|document_number`. `metadata.source_sequence` determina
el orden. Si no viene, el consumer usa los nanosegundos de
`metadata.event_timestamp`. Un evento duplicado o anterior queda `IGNORED`.

## Reemplazo transaccional

Cada mensaje representa el estado completo de un cliente. El consumer:

1. adquiere un advisory lock por cliente;
2. aplica la identidad solamente si la secuencia es más reciente;
3. actualiza los perfiles de cuenta;
4. elimina las relaciones anteriores del cliente;
5. reconstruye persona, contactos, vínculos y consolidaciones;
6. confirma el offset únicamente después del commit.

Si falla una tabla, PostgreSQL revierte las siete operaciones.

## Configuración

| Variable | Valor local |
|---|---|
| `POSTGRES_R2DBC_URL` | `r2dbc:postgresql://localhost:5432/customer_event_consumer` |
| `POSTGRES_USER` | `customer_consumer` |
| `POSTGRES_PASSWORD` | requerida |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `KAFKA_TOPIC_CUSTOMER_EVENTS` | `bhxw.tapp.customer.cdc.v1` |
| `KAFKA_CONSUMER_GROUP` | `customer_event_consumer_v1` |
| `KAFKA_CONCURRENCY` | `1` para un pod y procesamiento ordenado |
| `DB_INIT_MODE` | `never`; usar `always` solo para inicialización local |

Los virtual threads de Spring permanecen habilitados. Reactor y R2DBC continúan
ejecutando el camino principal de forma no bloqueante.

## Validación

```powershell
.\gradlew.bat clean check bootJar
```

Las pruebas unitarias no requieren infraestructura. Para ejecutar las pruebas de
integración con PostgreSQL:

```powershell
$env:CUSTOMER_TEST_R2DBC_URL = 'r2dbc:postgresql://customer_test@127.0.0.1:55432/customer_dynamic_test'
.\gradlew.bat clean check
```

## Prueba local de 500 mensajes

```powershell
.\scripts\publish-500-customer-events.ps1
```

El script genera el contrato nuevo, usa una clave Kafka por documento, evita publicar
una línea vacía al final y valida las filas persistidas en PostgreSQL.
