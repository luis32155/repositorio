# MongoDB local con Docker

## Iniciar

Desde PowerShell, dentro de esta carpeta:

```powershell
.\start-mongo.ps1
```

También puede iniciarse directamente:

```powershell
docker compose up -d
```

## Verificar

```powershell
docker ps --filter "name=mongo"
docker logs mongo
```

```powershell
docker exec mongo mongosh `
  --username local_admin `
  --password local_mongo_password `
  --authenticationDatabase admin `
  --eval "db.adminCommand('ping')"
```

## Conexión

```text
mongodb://local_admin:local_mongo_password@localhost:27017/customer_event_consumer?authSource=admin
```

Variables para IntelliJ:

```text
SPRING_DATA_MONGODB_URI=mongodb://local_admin:local_mongo_password@localhost:27017/customer_event_consumer?authSource=admin
```

## Detener conservando los datos

```powershell
.\stop-mongo.ps1
```

## Eliminar también los datos

```powershell
.\delete-mongo-data.ps1
```

El consumer actual del proyecto utiliza PostgreSQL y R2DBC. Esta configuración inicia MongoDB de manera independiente; no cambia automáticamente la persistencia del proyecto.

