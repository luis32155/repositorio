# Prueba de tamaño de payloads XML

Este directorio contiene fixtures XML con datos dummy y sus mediciones reproducibles.

## Resultado rápido

- `ReqPay` realista: 2 227 bytes sin firma y 4 498 bytes con firma dummy.
- `ReqPay` conservador: 5 467 bytes sin firma y 7 738 bytes con firma dummy.
- `ReqPay` máximo recibido: 9 721 bytes sin firma y 11 992 bytes con firma dummy.
- `RespListAccount` supera 3 KiB a partir de siete cuentas sin firma.
- Con la firma dummy de 2 271 bytes, una respuesta de una cuenta mide 3 172 bytes.

Todos los XML generados pasaron validación de estructura XML. Las firmas son datos dummy y no tienen validez criptográfica.

La explicación completa está en `docs/cdc-workaround/mongodb-sizing-actualizado-2027-2032.md`.

## Reproducción

```powershell
python scripts/generate_payload_size_test.py `
  --output outputs/mongodb-sizing/payload-size-test
```

