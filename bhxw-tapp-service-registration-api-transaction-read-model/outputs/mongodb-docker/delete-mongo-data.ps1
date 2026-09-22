$ErrorActionPreference = "Stop"
$ComposeFile = Join-Path $PSScriptRoot "docker-compose.yml"

docker compose --file $ComposeFile down --volumes

if ($LASTEXITCODE -ne 0) {
    throw "MongoDB and its volume could not be removed."
}

Write-Host "MongoDB stopped and mongo_data was deleted."

