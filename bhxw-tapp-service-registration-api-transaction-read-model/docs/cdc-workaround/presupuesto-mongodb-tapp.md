# Presupuesto económico para MongoDB — TAPP

**Estado:** estimación presupuestal preliminar  
**Fecha de referencia de precios:** 28 de agosto de 2026  
**Moneda:** USD, antes de impuestos  
**Plataforma considerada:** MongoDB Atlas sobre Google Cloud  
**Caso:** Transaction Read Model alimentado continuamente desde Kafka

> Este documento no es una cotización comercial. Los precios de Atlas dependen de la nube, región, topología, almacenamiento, soporte, contrato y descuentos corporativos. Antes de aprobar presupuesto debe generarse una estimación formal en la calculadora de Atlas y solicitarse una propuesta a MongoDB/Compras.

## 1. Resumen ejecutivo

Para el volumen preliminar de TAPP —200 000 transacciones mensuales, 2.4 millones de documentos retenidos durante un año y aproximadamente 8–11 GB incluyendo índices y holgura— el almacenamiento no es el principal costo. Los componentes que más influirán en el presupuesto son:

1. tier de cómputo del clúster productivo;
2. ambientes no productivos;
3. requisitos de seguridad bancaria;
4. soporte contratado;
5. alta disponibilidad y Disaster Recovery;
6. operación, monitoreo y guardias;
7. red privada, transferencia entre regiones y backups.

Estimación orientativa de costos directos, excluyendo soporte pagado, personal, impuestos y descuentos:

| Escenario | Costo mensual estimado | Costo anual estimado | Uso recomendado |
|---|---:|---:|---|
| Económico controlado | USD 450–600 | USD 5 400–7 200 | Producción pequeña con M20, ambientes reducidos y sin DR multi-región. |
| Recomendado regulado | USD 1 100–1 350 | USD 13 200–16 200 | Producción M30, ambientes representativos, seguridad, backup y observabilidad. |
| Alta disponibilidad / DR | USD 1 500–3 000+ | USD 18 000–36 000+ | Multi-región, copias adicionales, mayor soporte y pruebas de continuidad. |

Para TAPP, el escenario **recomendado regulado** es el mejor punto de partida presupuestal. El tier definitivo debe confirmarse mediante pruebas de carga y con el especialista de MongoDB.

## 2. Supuestos de dimensionamiento

| Variable | Supuesto actual |
|---|---:|
| Transacciones mensuales | 200 000 |
| Transacciones anuales | 2 400 000 |
| Documento promedio | 2 KB |
| Documento conservador para sizing | 3 KB |
| Datos anuales sin comprimir | 4.8–7.2 GB |
| Datos + índices + 30% de margen | 8–11 GB |
| Retención | 365 días con TTL |
| Hot data | 30 días propuestos |
| Escrituras pico preliminares | 3.1/s |
| Lecturas pico preliminares | 3/s |
| Prueba mínima | 5 lecturas/s y 5 escrituras/s |
| Patrón de consultas | 95% CRUD y 5% agregación |

Si se almacenan request, response y firma XML completos, el volumen puede superar 34 GB/año antes de índices y holgura. En ese caso se debe recalcular tanto disco como backup y working set.

## 3. Precios base de MongoDB Atlas

MongoDB publica los siguientes precios base para clústeres dedicados. Atlas factura por hora y emite facturas mensuales. El precio real puede cambiar según configuración y región.

| Tier | RAM | vCPU | Almacenamiento indicado | Precio base/hora | Aproximado/mes¹ | Aproximado/año |
|---|---:|---:|---:|---:|---:|---:|
| M10 | 2 GB | 2 | 10–128 GB | USD 0.08 | USD 58.40 | USD 700.80 |
| M20 | 4 GB | 2 | 20–256 GB | USD 0.20 | USD 146.00 | USD 1 752.00 |
| M30 | 8 GB | 2 | 40–512 GB | USD 0.54 | USD 394.20 | USD 4 730.40 |
| M40 | 16 GB | 4 | 80 GB–1 TB | USD 1.04 | USD 759.20 | USD 9 110.40 |
| M50 | 32 GB | 8 | 160 GB–4 TB | USD 2.00 | USD 1 460.00 | USD 17 520.00 |

¹ Cálculo con 730 horas por mes. No incluye backups, red, endpoints privados, soporte ni add-ons.

Fuente: [MongoDB Atlas Pricing](https://www.mongodb.com/pricing).

### 3.1 Lectura económica para TAPP

- **M10:** su almacenamiento base de 10 GB resulta ajustado frente al cálculo de 8–11 GB. También ofrece solo 2 GB de RAM. Puede servir para QA o desarrollo, pero no deja una holgura cómoda para producción.
- **M20:** 20 GB base y 4 GB de RAM. Es el punto mínimo económicamente razonable para el volumen actual, sujeto a prueba de carga y requisitos de seguridad.
- **M30:** 40 GB base y 8 GB de RAM. Es la referencia presupuestal recomendada para un ambiente productivo regulado porque ofrece más working set y margen de crecimiento.
- **M40 o superior:** considerar cuando las pruebas demuestren presión de CPU/memoria, índices mayores, crecimiento de consultas o requisitos de disponibilidad superiores.

MongoDB indica que los clústeres dedicados comienzan en M10 y ofrecen recursos garantizados, backups más robustos y herramientas de diagnóstico. Los Flex clusters comparten hardware y están orientados principalmente a aprendizaje o exploración, por lo que no deberían asumirse como plataforma productiva bancaria sin aprobación explícita. Véase [Billing Breakdown and Optimization](https://www.mongodb.com/docs/atlas/billing/billing-breakdown-optimization/).

## 4. Costos regulares que deben presupuestarse

### 4.1 Cómputo del clúster

Es normalmente el mayor cargo directo de Atlas. Se factura mientras el clúster permanece activo.

Presupuestar:

- clúster productivo;
- nodos o regiones adicionales;
- réplicas de solo lectura o analytics, si se incorporan;
- crecimiento automático a un tier superior;
- ambientes QA, UAT, Performance y Desarrollo.

### 4.2 Almacenamiento

El tier incluye una cantidad base de disco. Se generan cargos adicionales cuando:

- se aumenta el almacenamiento por encima del valor incluido;
- se activa auto-scaling de storage;
- se incorporan índices adicionales;
- se conservan documentos o eventos por más tiempo;
- se almacenan XML crudos, logs o auditoría dentro del documento;
- se agregan shards o nodos en otras regiones.

El presupuesto debe revisarse al llegar al 60–70% del almacenamiento aprovisionado, sin esperar a un evento automático de escalamiento.

### 4.3 Backups y Point-in-Time Recovery

Para Google Cloud, MongoDB publica un rango de **USD 0.08–0.12 por GB/mes** para Cloud Backup. Los snapshots son incrementales en la mayoría de los casos, pero el consumo efectivo depende de la tasa de cambio, política de retención, topología y copias adicionales.

Ejemplo base:

```text
20 GB de backup equivalente × USD 0.08–0.12 = USD 1.60–2.40/mes
```

Para un presupuesto realista de TAPP se recomienda reservar **USD 5–15/mes** inicialmente, porque pueden coexistir varios snapshots y oplog. Copiar backups a una región adicional puede aproximarse a duplicar el almacenamiento de backup y agregar transferencia interregional.

Atlas cobra además **USD 0.125 por GB exportado** a un bucket de AWS, Azure o Google Cloud, más el costo de transferencia del proveedor.

Fuente: [Atlas Cluster Configuration Costs — Backup](https://www.mongodb.com/docs/atlas/billing/cluster-configuration-costs/).

### 4.4 Transferencia de datos

Atlas no cobra por datos entrantes al clúster. Sí pueden existir cargos por:

- respuestas y datos salientes;
- tráfico entre regiones;
- réplicas multi-región;
- exportación de logs;
- restauración o descarga de snapshots;
- tráfico entre proveedores de nube;
- endpoints privados.

Con el volumen actual, el tráfico funcional probablemente será pequeño frente al cómputo. Sin embargo, una topología multi-región o exportación intensiva de logs puede cambiar esa relación. MongoDB indica que la mayoría de clientes mantiene la transferencia por debajo del 10% del presupuesto; se debe tratar esto como referencia, no como garantía.

Fuente: [Atlas Data Transfer Costs](https://www.mongodb.com/docs/atlas/billing/data-transfer-costs/).

### 4.5 Conectividad privada

En GCP se espera usar Private Service Connect o el mecanismo privado aprobado por Plataforma. El costo puede incluir:

- cargo horario por endpoint de Atlas;
- unidades de capacidad del endpoint;
- procesamiento de datos del lado GCP;
- endpoints separados por ambiente y región;
- tráfico interregional.

Hasta obtener una topología final, reservar una provisión de **USD 30–150/mes** para red privada y transferencia. Este rango es una contingencia presupuestal, no una tarifa oficial.

Fuentes: [Atlas Private Endpoint Billing](https://www.mongodb.com/docs/atlas/billing/additional-services/) y [Google Cloud VPC Pricing](https://cloud.google.com/vpc/pricing).

### 4.6 Seguridad avanzada

Para un proyecto bancario se deben considerar las siguientes ampliaciones:

| Funcionalidad | Impacto publicado por Atlas |
|---|---:|
| Database Auditing | 10% adicional sobre el costo horario de los clústeres dedicados, salvo planes donde esté incluido. |
| LDAP o cifrado con claves administradas por el cliente | 15% adicional sobre el costo de cada clúster, salvo planes Enterprise/Platinum donde esté incluido. |
| KMS del proveedor cloud | Cargos adicionales por solicitudes y administración de claves. |

Si se aplican el uplift de auditoría y el de LDAP/KMS, se debe reservar hasta **25% adicional** sobre los clústeres afectados, sujeto al plan comercial definitivo.

Ejemplo M30:

```text
USD 394.20 × 25% = USD 98.55 adicionales por mes
```

Fuente: [Atlas Additional Services](https://www.mongodb.com/docs/atlas/billing/additional-services/).

### 4.7 Soporte MongoDB

Atlas incluye soporte Basic sin costo. Los planes Developer, Pro, Enterprise y Platinum ofrecen menores tiempos de respuesta y servicios adicionales, pero sus precios deben consultarse en Atlas o con Ventas.

Para TAPP se debe solicitar una cotización que incluya:

- atención 24×7;
- tiempo de primera respuesta para incidentes Sev-1;
- escalamiento telefónico;
- soporte de rendimiento y buenas prácticas;
- acompañamiento en lanzamientos y DR;
- cobertura de todos los ambientes y organizaciones Atlas.

El soporte pagado puede convertirse en uno de los costos más relevantes y no está incluido en los escenarios numéricos de este documento.

Fuente: [MongoDB Atlas Support Plans](https://www.mongodb.com/services/support/atlas-support-plans).

### 4.8 Monitoreo y logs

Atlas proporciona métricas y herramientas propias, pero el proyecto puede generar costos adicionales por:

- exportación a Datadog, Splunk, Elastic, Cloud Logging u otra plataforma;
- volumen y retención de logs;
- métricas de alta cardinalidad;
- trazas OpenTelemetry;
- alertas, dashboards y almacenamiento de auditoría.

Reserva inicial sugerida: **USD 20–150/mes**, dependiendo de si la organización ya posee una plataforma central y del volumen de logs.

### 4.9 Personal operativo

Aunque Atlas es administrado, no elimina la necesidad de operación. Deben presupuestarse horas de:

- DBA o especialista MongoDB;
- SRE/Plataforma;
- seguridad y gestión de accesos;
- FinOps y revisión de facturación;
- soporte de aplicaciones;
- guardia y gestión de incidentes;
- pruebas de restauración y DR.

Para una base pequeña administrada puede estimarse inicialmente **0.10–0.25 FTE compartido**. En un entorno regulado con guardia, auditoría y DR puede requerirse **0.25–0.50 FTE distribuido** entre varios equipos.

Fórmula:

```text
Costo operativo mensual = costo mensual cargado de un FTE × porcentaje de dedicación
```

Ejemplo únicamente metodológico:

```text
USD 5 000/FTE-mes × 20% = USD 1 000/mes de operación
```

Debe reemplazarse por las tarifas internas o del proveedor. Este costo no está incluido en los escenarios de infraestructura.

### 4.10 Impuestos, moneda y contratación

Los precios publicados están en USD y normalmente no incluyen:

- IGV u otros impuestos aplicables;
- retenciones por servicio del exterior;
- variación USD/PEN;
- comisión del marketplace o canal de compra;
- mínimos de soporte;
- compromisos contractuales;
- descuentos corporativos.

Finanzas y Compras deben definir un tipo de cambio presupuestal y una reserva por variación cambiaria. No debe convertirse el presupuesto a soles usando solamente el tipo de cambio del día.

## 5. Ambientes y costo mensual base

### 5.1 Alternativa económica

| Ambiente | Configuración ilustrativa | Costo base/mes |
|---|---|---:|
| Producción | M20 24×7 | USD 146.00 |
| UAT/Staging | M10 24×7 | USD 58.40 |
| QA | Flex | Hasta USD 30.00 |
| Desarrollo | Flex | USD 8.00–30.00 |
| Performance | M20 activo 25% del mes | USD 36.50 |
| **Subtotal de cómputo** | | **USD 278.90–300.90** |

Sumando seguridad, backup, red, monitoreo y 20% de contingencia, el rango directo esperado es aproximadamente **USD 450–600/mes**.

Riesgos:

- menor fidelidad entre QA/DEV y producción;
- Flex puede no ofrecer capacidades de seguridad o red requeridas;
- M20 productivo debe ser validado con carga real;
- no incorpora región secundaria de DR.

### 5.2 Alternativa recomendada para un entorno regulado

| Ambiente | Configuración ilustrativa | Costo base/mes |
|---|---|---:|
| Producción | M30 24×7 | USD 394.20 |
| UAT/Staging | M20 24×7 | USD 146.00 |
| QA/SIT | M10 24×7 | USD 58.40 |
| Desarrollo | Flex, si Seguridad lo permite | Hasta USD 30.00 |
| Performance | M20 activo 25% del mes | USD 36.50 |
| **Subtotal de cómputo** | | **USD 665.10** |

Presupuesto adicional mensual:

| Concepto | Estimación mensual |
|---|---:|
| Seguridad avanzada en PROD/UAT | USD 100–135 |
| Backups y PITR | USD 10–30 |
| Private endpoints y transferencia | USD 50–150 |
| Monitoreo y logs | USD 50–150 |
| Subtotal antes de contingencia | USD 875–1 130 |
| Contingencia 20% | USD 175–226 |
| **Total directo estimado** | **USD 1 050–1 356/mes** |

Redondeo presupuestal recomendado: **USD 1 100–1 350/mes**, o **USD 13 200–16 200/año**.

No incluye soporte pagado, mano de obra, impuestos, Kafka, GKE ni herramientas corporativas compartidas.

### 5.3 Alta disponibilidad y Disaster Recovery

Una topología multi-región puede incrementar:

- cantidad y tipo de nodos;
- almacenamiento replicado;
- transferencia interregional;
- copias de backup;
- endpoints privados;
- pruebas periódicas y soporte.

Reserva inicial ilustrativa: **USD 1 500–3 000+/mes** de infraestructura directa. El valor debe obtenerse en la calculadora de Atlas con las regiones, prioridades y nodos definitivos.

## 6. Costo unitario por transacción

El costo unitario ayuda a controlar crecimiento y comparar alternativas.

```text
Costo por transacción = costo mensual total / transacciones mensuales
Costo por 1 000 transacciones = costo mensual total / 200
```

Para el escenario recomendado, excluyendo soporte, personal e impuestos:

| Costo mensual | Costo por transacción | Costo por 1 000 transacciones |
|---:|---:|---:|
| USD 1 100 | USD 0.0055 | USD 5.50 |
| USD 1 350 | USD 0.0068 | USD 6.75 |

El costo por transacción disminuye si el clúster mantiene capacidad ociosa y aumenta el volumen sin requerir un tier mayor. Puede aumentar abruptamente cuando el working set exige escalar a otro tier.

## 7. Costos únicos de implementación

Estos costos no forman parte de la operación mensual, pero deben incorporarse al presupuesto del proyecto:

| Actividad | Esfuerzo orientativo |
|---|---:|
| Diseño final de datos, índices y seguridad | 1–2 semanas |
| Provisionamiento Atlas, red privada, KMS y accesos | 1–2 semanas |
| Carga inicial/backfill y reconciliación | 1–2 semanas |
| Pruebas de carga, resiliencia y recuperación | 1–2 semanas |
| Observabilidad, alertas y runbooks | 1 semana |
| Certificación de seguridad y salida a producción | 1–3 semanas, según aprobaciones |

Fórmula de costo:

```text
Costo único = Σ (horas por rol × tarifa por hora) + servicios profesionales + ambientes temporales
```

Roles habituales:

- arquitecto;
- desarrollador backend/data;
- especialista MongoDB;
- plataforma/cloud;
- seguridad;
- QA/performance;
- operaciones.

## 8. Presupuesto total de propiedad — TCO

El presupuesto anual correcto no debe limitarse a la factura Atlas.

```text
TCO anual =
  infraestructura Atlas y cloud
  + soporte MongoDB
  + personal operativo y guardias
  + monitoreo y logs
  + pruebas de DR y restauración
  + impuestos y variación cambiaria
  + amortización de costos únicos
  + contingencia
```

Plantilla:

| Categoría | Mensual | Anual | Responsable | Fuente del valor |
|---|---:|---:|---|---|
| Atlas compute | | | Plataforma | Calculadora Atlas |
| Storage adicional | | | Plataforma | Calculadora Atlas |
| Backup/PITR | | | Plataforma | Política de backup |
| Transferencia/red privada | | | Network/Cloud | Calculadoras Atlas/GCP |
| Seguridad avanzada/KMS | | | Seguridad | Atlas + GCP KMS |
| Soporte MongoDB | | | Compras | Cotización comercial |
| Monitoreo/logs | | | Observabilidad | Plataforma corporativa |
| DEV/QA/UAT/PERF | | | Proyecto | Diseño de ambientes |
| Operación/guardia | | | Operaciones | Tarifas internas |
| Impuestos y FX | | | Finanzas | Política corporativa |
| Contingencia 15–25% | | | Proyecto | Riesgo aprobado |
| **Total** | | | | |

## 9. Controles para evitar sobrecostos

### Mensualmente

- comparar factura real frente al presupuesto;
- calcular costo por 1 000 transacciones;
- revisar almacenamiento, índices y tasa de crecimiento;
- revisar consumo y retención de backups;
- identificar transferencia interregional;
- eliminar ambientes o endpoints abandonados;
- verificar tiers y límites de auto-scaling;
- revisar exportación de logs y consultas ineficientes.

### Trimestralmente

- ejecutar prueba de restauración;
- revisar índices utilizados/no utilizados;
- recalcular working set y capacidad;
- revisar RPO/RTO y retención;
- ejecutar carga representativa;
- revisar contrato, créditos y consumo proyectado.

### Anualmente

- renegociar suscripción y soporte;
- revisar descuentos por compromiso;
- actualizar previsión de crecimiento a tres años;
- ejecutar prueba completa de DR;
- presupuestar upgrades y cambios de versión;
- validar impuestos, tipo de cambio y canal de compra.

## 10. Auto-scaling y control presupuestal

Atlas permite auto-scaling de cómputo y almacenamiento. Es útil para disponibilidad, pero debe configurarse con límites económicos:

- mínimo y máximo de tier aprobados;
- alertas ante cada cambio de tier;
- presupuesto mensual y alertas al 50%, 75%, 90% y 100%;
- revisión automática de clusters sin actividad;
- almacenamiento máximo autorizado;
- procedimiento de retorno a un tier menor;
- registro del motivo de cada escalamiento.

Para TAPP se puede evaluar inicialmente un rango **M20–M30** o **M30–M40**, dependiendo del tier aprobado después de las pruebas. MongoDB permite limitar el mínimo y máximo del auto-scaling para controlar costos.

Fuente: [Atlas Auto-Scaling](https://www.mongodb.com/docs/atlas/cluster-autoscaling/).

## 11. Riesgos presupuestales

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Guardar XML completo sin recalcular | Mayor disco, backup y red | Mantener proyección compacta o archivar XML fuera del read model. |
| `events` crece hasta 200 elementos | Documento e índices más grandes | Medir BSON promedio/p95 y limitar historial. |
| Auto-scaling sin límite máximo | Aumento automático de factura | Definir techo de tier/storage y alertas. |
| Multi-región no contemplada | Cómputo y transferencia mayores | Cotizar topología DR antes de aprobación. |
| Muchos ambientes 24×7 | Duplicación del costo fijo | Usar ambientes efímeros cuando sea permitido. |
| Backups con retención excesiva | Mayor almacenamiento | Aplicar política por criticidad y probar restauración. |
| Logs sensibles o verbosos | Costos altos y riesgo de seguridad | Muestreo, redacción y retención diferenciada. |
| Índices innecesarios | Más RAM, disco y costo | Revisar `explain`, Query Insights y uso real. |
| Soporte empresarial omitido | Desviación material del presupuesto | Solicitar cotización desde el inicio. |
| Variación USD/PEN | Sobrecosto local | Tipo de cambio presupuestal y reserva financiera. |

## 12. Costos no incluidos

Los siguientes componentes forman parte de la solución completa, pero no del presupuesto MongoDB de este documento:

- clúster Kafka y Schema Registry;
- pods GKE del bridge Kafka–MongoDB;
- Apigee, Akamai y balanceadores;
- HashiCorp Vault o Secret Manager;
- pipeline CI/CD y Artifact Registry;
- observabilidad corporativa ya contratada;
- desarrollo de APIs y productores CDC;
- conectividad desde Core/AS400;
- licencias o soporte de herramientas CDC;
- horas de proyecto y soporte, salvo que se incorporen con la fórmula de TCO.

## 13. Información necesaria para una cotización definitiva

1. proveedor y región exactos del clúster Atlas;
2. tier productivo aprobado después de las pruebas;
3. topología de réplica y regiones de DR;
4. tamaño BSON promedio, p95 y máximo;
5. cantidad real de eventos por transacción;
6. almacenamiento de XML completo o solo proyección;
7. retención de datos, backups y oplog;
8. cantidad de endpoints privados por ambiente;
9. volumen de transferencia y exportación de logs;
10. características de seguridad requeridas;
11. plan de soporte y SLA comercial;
12. ambientes 24×7 frente a ambientes temporales;
13. crecimiento anual esperado;
14. impuestos, marketplace, descuento y tipo de cambio corporativo.

## 14. Recomendación final

Para reservar presupuesto sin subestimar el servicio:

1. usar **M30 productivo** como referencia presupuestal, aunque M20 quede como opción después de las pruebas;
2. reservar **USD 1 100–1 350 mensuales** para infraestructura directa del escenario recomendado;
3. cotizar por separado soporte Enterprise/Pro, personal operativo e impuestos;
4. incorporar **20% de contingencia** durante el primer año;
5. validar el costo en la [calculadora oficial de Atlas](https://www.mongodb.com/pricing/calculator/estimate/new/cluster-configuration/new);
6. recalcular después de la prueba de carga y al recibir el primer mes de métricas reales;
7. presentar a Finanzas tres escenarios: económico, recomendado y alta disponibilidad/DR.

## 15. Fuentes oficiales

Consultadas el 28 de agosto de 2026:

- [MongoDB Atlas Pricing](https://www.mongodb.com/pricing)
- [Atlas Pricing Calculator](https://www.mongodb.com/pricing/calculator/estimate/new/cluster-configuration/new)
- [Billing Breakdown and Optimization](https://www.mongodb.com/docs/atlas/billing/billing-breakdown-optimization/)
- [Cluster Configuration and Backup Costs](https://www.mongodb.com/docs/atlas/billing/cluster-configuration-costs/)
- [Atlas Data Transfer Costs](https://www.mongodb.com/docs/atlas/billing/data-transfer-costs/)
- [Atlas Additional Services and Security Uplifts](https://www.mongodb.com/docs/atlas/billing/additional-services/)
- [MongoDB Atlas Support Plans](https://www.mongodb.com/services/support/atlas-support-plans)
- [Atlas Auto-Scaling](https://www.mongodb.com/docs/atlas/cluster-autoscaling/)
- [Google Cloud VPC Pricing](https://cloud.google.com/vpc/pricing)

