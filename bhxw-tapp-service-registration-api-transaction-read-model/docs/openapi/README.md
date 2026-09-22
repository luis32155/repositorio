# Contratos HTTP

## Contrato canónico Niubiz/Juspay - CBS

`juspay-peru-cbs-api.yaml` es el contrato que debe utilizarse para la integración
externa. Fue corregido con los cuerpos y campos de **Juspay-Peru CBS API Specs**.

| Operación | Endpoint propuesto | Dato de idempotencia |
| --- | --- | --- |
| List Accounts | `POST /v1/cbs/accounts/list` | No definido |
| Validate Card Details | `POST /v1/cbs/cards/validate` | No definido |
| Balance Enquiry | `POST /v1/cbs/accounts/balance` | No definido |
| Credit Money | `POST /v1/cbs/transactions/credit` | `gatewayTransactionId` |
| Debit Money | `POST /v1/cbs/transactions/debit` | `gatewayTransactionId` |
| Status Check | `POST /v1/cbs/transactions/status` | Consulta por `gatewayTransactionId` |

La reversa de débito **no es un endpoint separado**. Se procesa mediante Credit
Money con `type=DEBIT_REVERSAL`. Los reembolsos usan la misma operación con
`type=REFUND`.

### Convenciones confirmadas por el documento

- Los nombres JSON se conservan en `camelCase`.
- Las respuestas contienen `status`, `responseCode`, `responseMessage` y un
  `payload` opcional.
- Credit Money acepta `TRANSACTION`, `DEBIT_REVERSAL` y `REFUND`.
- Debit Money acepta `TRANSACTION` y `MANDATE`.
- Status Check acepta `DEBIT_TRANSACTION`, `CREDIT_TRANSACTION` y
  `DEBIT_REVERSAL`.
- Validate Card Details recibe los últimos seis dígitos en `cardNumber`, la cuenta
  en `accountIdentifier` y la expiración `MMYY` en `expiry`.
- `gatewayTransactionId` es la clave de idempotencia CBS para créditos y débitos.

### Propuesta de transporte y seguridad

El PDF no define URLs, verbos HTTP, API key, mTLS ni headers de trazabilidad. El
OpenAPI propone `POST`, `X-API-Key`, mTLS, `X-Correlation-Id` y
`X-Channel-Id=NIUBIZ_JUSPAY` basándose en los diagramas de Akamai/Apigee.

### Localización pendiente para Perú

El documento aún conserva conceptos de India. Antes de aprobar `1.0.0` se debe
acordar con Niubiz/Juspay:

1. Sustitución u omisión de `lk`, `ac`, `sa`, `aadhaarEnabled` y
   `aadhaarNumber`; el texto sugiere DNI como equivalente local.
2. Sustitución de `ifsc`, `payerIfsc` y `payeeIfsc` por el código de ruteo local.
3. Uso de PEN en lugar de INR y si se requiere un campo explícito de moneda.
4. Formato y zona horaria de `transactionTimestamp` y `updatedAt`.
5. Catálogos de `status`, `cbsStatus`, `responseCode` y `cbsResponseCode`.
6. Paths, hostname, SLA, timeouts y códigos HTTP definitivos.

## Contratos internos anteriores

Los archivos `customer-profile-api.yaml`, `accounts-api.yaml`,
`debit-card-api.yaml`, `debit-payments-api.yaml` y `credit-payments-api.yaml`
fueron inferidos de los diagramas de dominio. Permanecen como borradores internos,
pero **no deben utilizarse como contrato Niubiz/Juspay**; el PDF no contiene
Customer Profile ni Account Detail y sus estructuras no coinciden con el CBS API.

## Eventos CDC

`../asyncapi-cdc.yaml` describe las cargas iniciales y eventos CDC de Customer y
Account. `../asyncapi.yaml` describe la proyección de transacciones. Los esquemas
Avro se encuentran en `../avro/`. Estos contratos asíncronos son internos y no
forman parte del CBS API entregado por Juspay.
