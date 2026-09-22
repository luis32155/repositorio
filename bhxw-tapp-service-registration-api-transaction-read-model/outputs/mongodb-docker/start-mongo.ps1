$ErrorActionPreference = "Stop"
$ComposeFile = Join-Path $PSScriptRoot "docker-compose.yml"

docker compose --file $ComposeFile up --detach

if ($LASTEXITCODE -ne 0) {
    throw "MongoDB could not be started."
}

Write-Host "Waiting for MongoDB..."

$Deadline = [DateTimeOffset]::UtcNow.AddSeconds(120)

do {
    $Status = docker inspect `
        --format "{{.State.Health.Status}}" `
        mongo 2>$null

    if ($Status -eq "healthy") {
        Write-Host "MongoDB is ready."
        Write-Host "Host: localhost"
        Write-Host "Port: 27017"
        Write-Host "Database: customer_event_consumer"
        Write-Host "Username: local_admin"
        Write-Host "Password: local_mongo_password"
        Write-Host "URI: mongodb://local_admin:local_mongo_password@localhost:27017/customer_event_consumer?authSource=admin"
        exit 0
    }

    Start-Sleep -Seconds 2
}
while ([DateTimeOffset]::UtcNow -lt $Deadline)

docker logs mongo
throw "MongoDB did not become healthy within 120 seconds."

