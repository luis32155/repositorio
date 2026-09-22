# Transaction Projection / Read Model

Microservicio consumidor Kafka que escucha `tapp.transaction.projection.v1` y mantiene la
vista consolidada de cada transacción en PostgreSQL mediante R2DBC.

## Arquitectura

```text
Outbox Publisher -> Kafka -> TransactionProjectionEventConsumer
                                -> TransactionProjectionService
                                -> PostgresTransactionProjectionAdapter
                                -> PostgreSQL transaction_read_model.transaction_projection
```

## Requisitos

- Java 25.
- Gradle compatible con Spring Boot 4.0.6.
- Docker Desktop para levantar PostgreSQL, Redis y Kafka localmente.
- Credenciales de Artifactory en `~/.gradle/gradle.properties` cuando corresponda.

## Levantar infraestructura local

```bash
docker compose up -d
```

Tópicos locales:

- `tapp.transaction.projection.v1`
- `tapp.transaction.projection.v1.retry`
- `tapp.transaction.projection.v1.dlq`

## Ejecutar

Reutiliza el Gradle Wrapper corporativo del proyecto base:

```bash
./gradlew clean bootRun
```

## Mensaje de ejemplo

Kafka key:

```text
TXN-001|ABC-001
```

Payload:

```json
{
  "payload": {
    "transaction_id": "TXN-001",
    "req_msg_id": "ABC-001",
    "event_type": "LogAuditDelivered",
    "aggregate_version": 9,
    "occurred_at": "2026-01-01T10:05:00Z",
    "transaction_status": "COMPLETED",
    "callback_status": "DELIVERED",
    "notification_status": "DELIVERED",
    "log_audit_status": "PERSISTED",
    "amount": 100.00,
    "currency": "PEN",
    "masked_account": "****3456",
    "started_at": "2026-01-01T10:00:00Z",
    "completed_at": "2026-01-01T10:05:00Z",
    "events": []
  },
  "metadata": {
    "event_id": "EVT-000009",
    "schema_version": "1.0",
    "published_at": "2026-01-01T10:05:01Z",
    "source_service": "outbox_publisher_service",
    "correlation_id": "correlation-001",
    "trace_id": "trace-001",
    "retry_count": 0
  }
}
```

## Reglas implementadas

- Identidad: `transaction_id + req_msg_id`.
- Kafka key obligatoria: `transaction_id|req_msg_id`.
- Idempotencia por `metadata.event_id`.
- Orden por `payload.aggregate_version`.
- Evento duplicado: no-op.
- Evento atrasado: no-op.
- Misma versión con otro `event_id`: error permanente y DLQ.
- Error de PostgreSQL: tres reintentos adicionales y luego DLQ.
- Retención lógica: `completed_at + 1 año` guardada en `expires_at` para depuración programada.

## Nota sobre Gradle Wrapper

El ZIP no incluye `gradle-wrapper.jar`. Copia las carpetas y archivos sobre el repositorio
base mostrado en IntelliJ, conservando su `gradlew`, `gradlew.bat` y `gradle/wrapper`.
