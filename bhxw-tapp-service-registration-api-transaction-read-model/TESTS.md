# Pruebas unitarias — Transaction Read Model

## Cobertura funcional incluida

- `TransactionProjectionServiceTest`
  - evento COMPLETED válido;
  - evento PROCESSING sin expiración;
  - evento nulo;
  - errores Bean Validation;
  - `schema_version` inválida;
  - `completed_at` faltante;
  - `completed_at` anterior a `started_at`;
  - propagación de errores PostgreSQL.

- `TransactionProjectionEventConsumerTest`
  - Kafka key correcta;
  - Kafka key incorrecta o nula;
  - valor Kafka nulo;
  - evento sin payload;
  - proyección sin resultado;
  - error reintentable;
  - handler de DLQ.

- `PostgresTransactionProjectionAdapterTest`
  - upsert condicional y atómico;
  - proyección activa sin expiración;
  - evento duplicado;
  - evento atrasado;
  - misma versión con otro `event_id`;
  - conflicto concurrente reintentable;
  - estado ausente después de conflicto;
  - error de acceso a PostgreSQL.

- Pruebas del contrato y dominio
  - envelope `payload` y `metadata`;
  - propiedades `snake_case`;
  - construcción del identificador de la proyección;
  - resultados `APPLIED`, `DUPLICATE_IGNORED` y `OUTDATED_IGNORED`.

- `HexagonalArchitectureTest`
  - dominio sin dependencias de infraestructura;
  - aplicación sin dependencias de adapters.

## Ejecución

Linux/macOS:

```bash
./gradlew clean test jacocoTestReport
```

Windows:

```powershell
.\gradlew.bat clean test jacocoTestReport
```

Reporte JaCoCo:

```text
build/reports/jacoco/test/html/index.html
```
