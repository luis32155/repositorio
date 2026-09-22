# MongoDB Sizing Questionnaire — TAPP

> **Actualización del 1 de septiembre de 2026:** la proyección 2027–2032 reemplaza el supuesto anterior de 200 000 transacciones mensuales. Consulte [`mongodb-sizing-actualizado-2027-2032.md`](./mongodb-sizing-actualizado-2027-2032.md) para los nuevos volúmenes, las pruebas XML y la recomendación vigente. Este archivo se conserva como línea base histórica.

**Estado:** respuesta preliminar para validación  
**Fecha:** 27 de agosto de 2026  
**Componente evaluado:** `transaction_projection` / Transaction Read Model  
**Volumen informado:** 200 000 transacciones mensuales

> Las cifras de capacidad son estimaciones iniciales. Los datos marcados como **propuestos** deben confirmarse con métricas productivas, negocio y el equipo de plataforma antes de contratar o aprovisionar capacidad.

## 1. Evidencia utilizada

### 1.1 XML entregado

Medición directa en UTF-8, sin compresión:

| Muestra | Tamaño medido | Observación |
|---|---:|---|
| `RespListAccount` | 2 363 bytes / 2.31 KiB | Contiene cinco elementos `Account`. |
| Un elemento `Account` | aproximadamente 375 bytes | El tamaño del listado crece con la cantidad de cuentas. |
| `ReqPay` | 9 721 bytes / 9.49 KiB | Muestra con campos rellenados a longitudes altas; no incluye una firma separada. |
| Firma mostrada en la captura | aproximadamente 2 KiB | Valor reportado, no incluido como bloque separado en el texto recibido. |

La captura también indica que otros responses sin firma pesan aproximadamente 1 KB y que el request sin firma de mayor tamaño sería 3 KB. Esta información **no coincide** con el `ReqPay` pegado, que mide 9.49 KiB. Se debe confirmar si ese XML es un caso máximo artificial, un contrato actualizado o el tamaño que realmente viajará en producción.

Para `RespListAccount`, una aproximación basada en la muestra es:

```text
tamaño ≈ 488 bytes fijos + 375 bytes × cantidad de cuentas
```

Ejemplos aproximados:

| Cuentas retornadas | Tamaño del XML |
|---:|---:|
| 1 | 0.84 KiB |
| 5 | 2.31 KiB |
| 10 | 4.14 KiB |
| 20 | 7.80 KiB |
| 50 | 18.79 KiB |

### 1.2 Modelo implementado en MongoDB

El código actual no almacena el request/response XML completo. La colección `transaction_projection` guarda una proyección compacta con:

- identidad `transaction_id + req_msg_id`;
- estados de transacción, callback, notificación y auditoría;
- importe, moneda y cuenta enmascarada;
- timestamps;
- metadatos del último evento;
- historial resumido en `events`;
- fecha `expires_at` para eliminación automática.

Índices implementados:

1. índice único compuesto `transaction_id + req_msg_id`;
2. índice TTL por `expires_at`.

Retención implementada: **365 días después de `completed_at`**.

## 2. Respuestas propuestas para el formulario

| Pregunta | Respuesta para colocar en el cuestionario | Estado |
|---|---|---|
| Cantidad total de datos sin comprimir | **4.8–7.2 GB lógicos para un año**, considerando 2.4 millones de documentos y un promedio de 2–3 KB por proyección. Para capacidad se recomienda considerar datos + índices + 30% de holgura: aproximadamente **8–11 GB lógicos**, con al menos **20 GB por nodo**. Si se almacenan XML completos, el cálculo cambia a aproximadamente **34 GB/año antes de índices y holgura**. | Preliminar; confirmar si se guardará XML. |
| Tamaño promedio de un documento escrito | **2 KB promedio** para la proyección compacta; usar **3 KB conservadores** para sizing. Puede crecer a decenas de KB si `events` contiene muchos elementos. El `ReqPay` XML entregado mide 9.49 KiB, pero actualmente no forma parte del documento MongoDB. | Preliminar; medir BSON real y promedio de eventos. |
| Tamaño promedio de un documento leído | **2 KB promedio** y **3 KB conservadores**. La búsqueda de identidad devuelve normalmente un documento. | Preliminar. |
| Duración del período pico de escritura/ingesta | **2 horas diarias** como supuesto inicial. Para pruebas se debe mantener capacidad para ráfagas de al menos 5 escrituras/s. | Propuesto; requiere horario real. |
| Documentos escritos durante el pico | 200 000 documentos nuevos/mes equivalen a 6 667/día, 278/hora, 4.6/minuto o 0.077/s en promedio. Como cada transacción puede generar varios eventos, usando **4 eventos por transacción** se estiman 800 000 upserts/mes y 0.31 upserts/s promedio. Con factor pico 10×: **3.1 escrituras/s**, aproximadamente 185/minuto o 11 100/hora. Diseñar y probar con **5 escrituras/s** como mínimo. | El volumen mensual está informado; eventos por transacción y factor pico están por confirmar. |
| Duración del período pico de lectura/consulta | **2 horas diarias**, alineadas inicialmente al pico de transacciones. | Propuesto; requiere horario real. |
| Documentos leídos durante el pico | Si se realizan hasta tres lecturas por transacción durante el pico: **aproximadamente 3 documentos/s**, 180/minuto o 10 800/hora. Para pruebas usar al menos **5 lecturas/s**. Cada consulta de identidad retorna normalmente un documento. | Propuesto; confirmar consultas por transacción y concurrencia. |
| Tamaño total del índice | **0.5–1.0 GB para 2.4 millones de documentos**, considerando el índice único compuesto y el índice TTL actuales. Debe validarse con datos BSON y cardinalidades reales. | Estimado. |
| Período de datos activos o hot data | **30 días hot** propuestos; **365 días de retención total** conforme al TTL implementado. | Hot data por confirmar; retención está implementada. |
| Analítica/rollups vs. CRUD | **5% analítica / 95% búsquedas simples CRUD**. El componente es un read model transaccional y no debe usarse como plataforma analítica principal. | Propuesto. |
| Promedio de documentos por resultado | **1 documento por consulta** para búsquedas por `transaction_id + req_msg_id`. Para `ListAccount`, el resultado funcional puede contener varias cuentas, pero no corresponde a múltiples documentos de esta colección salvo que se cambie el modelo. | Confirmado para la identidad actual. |
| Tiempo promedio de sesión de usuario | **20 minutos**, usando el valor predeterminado del cuestionario. Para el microservicio backend este dato no aplica directamente; la concurrencia y tasa de requests son más relevantes. | Propuesto. |
| Cómo se cargará la base, frecuencia y cantidad | **Carga continua por Kafka**, mediante upsert idempotente. Aproximadamente **200 000 documentos nuevos/mes**. Con cuatro eventos por transacción: aproximadamente **800 000 operaciones de actualización/mes**. Puede existir una carga inicial/backfill ejecutada una sola vez. | Mecanismo confirmado; multiplicador de eventos por confirmar. |
| ¿Las transacciones tienen SLA? | **Sí, propuesto:** proyección Kafka→MongoDB p95 ≤ 500 ms y p99 ≤ 1 000 ms en operación normal, excluyendo indisponibilidades externas y backlog de recuperación. | Requiere aprobación. |
| SLA o rendimiento específico en milisegundos | **Lectura MongoDB/API:** p95 ≤ 100 ms y p99 ≤ 200 ms. **Escritura/upsert MongoDB:** p95 ≤ 100 ms. **Proyección extremo a extremo:** p95 ≤ 500 ms y p99 ≤ 1 000 ms. | Propuesto. |
| Consultas estáticas vs. ad-hoc | **Estáticas, definidas en el código de la aplicación.** No se prevén consultas ad-hoc de usuario. | Confirmado por el diseño actual. |
| Ventana batch/bulk | No existe batch recurrente en el flujo normal; la ingesta es continua. Para carga inicial o backfill se propone **00:00–04:00, hora de Lima**, con throttling y monitoreo del lag. | Propuesto. |
| Estrategia de archivamiento/purga | **Purga automática mediante índice TTL a los 365 días desde `completed_at`.** Si existe obligación de conservar por más tiempo, archivar previamente en almacenamiento de objetos antes de la expiración. | TTL confirmado; archivamiento pendiente. |
| Estrategia de backup | **Producción: backup continuo/PITR más snapshot diario.** Propuesta inicial: RPO ≤ 5 minutos y RTO ≤ 60 minutos. No productivo: snapshot diario o previo a pruebas destructivas. | Propuesto; requiere política corporativa. |
| Entornos no productivos | **DEV, QA/SIT, UAT/Staging y PERF.** DR debe tratarse como parte de la estrategia productiva de continuidad, no como un ambiente de desarrollo. | Propuesto. |
| Almacenamiento no productivo respecto a producción | **DEV 10%, QA/SIT 25%, UAT/Staging 50% y PERF 100% temporal.** PERF necesita volumen representativo para pruebas; puede aprovisionarse únicamente durante campañas. | Propuesto. |

## 3. Cálculo de capacidad

### 3.1 Documentos retenidos

```text
200 000 transacciones/mes × 12 meses = 2 400 000 documentos activos
```

El número de documentos se basa en una proyección consolidada por transacción. Los eventos posteriores actualizan el mismo documento y no crean uno nuevo.

### 3.2 Datos lógicos del read model

```text
Escenario nominal:
2 400 000 documentos × 2 KB = 4.8 GB/año

Escenario conservador:
2 400 000 documentos × 3 KB = 7.2 GB/año
```

Agregando hasta 1 GB de índices y 30% de holgura:

```text
Nominal:     (4.8 GB + 1.0 GB) × 1.30 = 7.54 GB
Conservador: (7.2 GB + 1.0 GB) × 1.30 = 10.66 GB
```

Se recomienda no aprovisionar exactamente el valor calculado. Una base inicial de **20 GB por nodo de datos** deja espacio para variación de BSON, mantenimiento, crecimiento temporal e índices.

### 3.3 Escenario alternativo: almacenar XML completo

Si cada documento incorporara el request máximo recibido, una firma de 2 KiB y el `RespListAccount` de cinco cuentas:

```text
9.49 KiB request + 2 KiB firma + 2.31 KiB response ≈ 13.8 KiB/transacción
13.8 KiB × 2 400 000 ≈ 34 GB lógicos/año
```

Con índices y 30% de holgura, se necesitarían aproximadamente **45–50 GB por nodo**. No es el diseño implementado actualmente y se desaconseja duplicar los XML en el read model salvo requisito de auditoría explícito.

### 3.4 Operaciones de escritura

MongoDB recibe una actualización por evento, no únicamente una inserción por transacción:

```text
upserts mensuales = 200 000 transacciones × eventos promedio por transacción
```

| Eventos promedio por transacción | Upserts mensuales | Promedio por segundo | Pico 10× |
|---:|---:|---:|---:|
| 1 | 200 000 | 0.077 | 0.77/s |
| 4 | 800 000 | 0.309 | 3.09/s |
| 10 | 2 000 000 | 0.772 | 7.72/s |

La prueba de rendimiento debe usar el multiplicador real y no solamente las transacciones nuevas.

## 4. Riesgos que afectan el sizing

1. `events` permite hasta 200 elementos y puede incrementar considerablemente el tamaño BSON. Se debe medir el promedio y p95 de eventos por transacción.
2. `RespListAccount` crece aproximadamente 375 bytes por cuenta en la muestra entregada.
3. El `ReqPay` pegado mide más de tres veces el máximo reportado en la captura.
4. No se conoce todavía la distribución horaria de las 200 000 transacciones mensuales.
5. No se conocen la concurrencia ni el número de lecturas/status checks por transacción.
6. El crecimiento anual del negocio no está incluido; para planificación se recomienda incorporar al menos un escenario de crecimiento a tres años.
7. El índice TTL elimina documentos de forma asíncrona; puede existir una pequeña cantidad adicional de datos pendientes de purga.
8. Cualquier nuevo índice, historial completo, XML crudo o campo de auditoría cambia la estimación.

## 5. Preguntas necesarias para cerrar el sizing

Responder estas preguntas permitirá reemplazar los valores provisionales:

1. **¿MongoDB guardará solamente la proyección compacta actual o también el request, response y firma XML completos?**
2. **¿Cuántos eventos/upserts genera en promedio una transacción y cuál es el p95?**
3. **¿Cuál es el mayor volumen observado en una hora, minuto y segundo?** Si no existe producción, indicar el TPS contractual esperado.
4. **¿Cuántas consultas o status checks se realizan por transacción y cuántos usuarios concurrentes se esperan?**
5. **Para `ListAccount`, cuántas cuentas tiene un cliente en promedio, p95 y máximo?**
6. **¿Los 365 días de retención son correctos? ¿Cuántos días deben permanecer hot: 30, 90 o 365?**
7. **¿Cuáles son los SLA oficiales p95/p99, disponibilidad, RPO y RTO?**
8. **¿Cuál es el crecimiento anual esperado de transacciones durante los próximos tres años?**
9. **¿Se requiere archivamiento regulatorio después de la purga de MongoDB? ¿Por cuántos años?**
10. **¿Qué ambientes y porcentajes de datos ya aprobó Plataforma: DEV, QA/SIT, UAT, PERF y DR?**

## 6. Recomendación preliminar

Para el diseño actual de proyección compacta, 200 000 transacciones mensuales y retención de un año:

- dimensionar inicialmente para **2.4 millones de documentos**;
- usar **3 KB por documento** como supuesto conservador hasta medir BSON;
- reservar **20 GB de almacenamiento por nodo de datos** como punto de partida;
- probar al menos **5 escrituras/s y 5 lecturas/s**;
- ejecutar adicionalmente una prueba de estrés de **10 escrituras/s** si el promedio supera cuatro eventos por transacción;
- conservar backup continuo/PITR y snapshots diarios en producción;
- revisar el sizing al recibir métricas reales de pico y nuevamente al 70% de utilización.

Esta recomendación debe recalcularse si se decide almacenar XML crudo, si el historial `events` crece significativamente o si la retención supera un año.
