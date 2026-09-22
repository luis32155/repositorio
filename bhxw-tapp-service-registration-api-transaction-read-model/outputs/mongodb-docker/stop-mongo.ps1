$ErrorActionPreference = "Stop"
$ComposeFile = Join-Path $PSScriptRoot "docker-compose.yml"

docker compose --file $ComposeFile down

if ($LASTEXITCODE -ne 0) {
    throw "MongoDB could not be stopped."
}

Write-Host "MongoDB stopped. The mongo_data volume was preserved."

