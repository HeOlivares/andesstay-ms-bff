# Levanta el BFF con validación JWT contra Azure AD (IDaaS).
# Uso (desde la raíz del repo):
#   .\scripts\run-azure.ps1

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

$env:SECURITY_MODE = "azure"
$env:AZURE_TENANT_ID = "cb0b9f53-0ba7-4f09-8da2-c2f5ab4b73ee"
$env:AZURE_ISSUER_URI = "https://login.microsoftonline.com/cb0b9f53-0ba7-4f09-8da2-c2f5ab4b73ee/v2.0"
$env:AZURE_AUDIENCE = "api://4cd6df9a-e2f7-4024-aea6-dd67c49709bc"
$env:MS_CATALOG_URL = if ($env:MS_CATALOG_URL) { $env:MS_CATALOG_URL } else { "http://localhost:8081" }
$env:MS_RESERVATIONS_URL = if ($env:MS_RESERVATIONS_URL) { $env:MS_RESERVATIONS_URL } else { "http://localhost:8082" }
$env:SERVER_PORT = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8080" }

Write-Host "SECURITY_MODE=$env:SECURITY_MODE"
Write-Host "AZURE_ISSUER_URI=$env:AZURE_ISSUER_URI"
Write-Host "AZURE_AUDIENCE=$env:AZURE_AUDIENCE"
Write-Host "MS_CATALOG_URL=$env:MS_CATALOG_URL"
Write-Host "MS_RESERVATIONS_URL=$env:MS_RESERVATIONS_URL"
Write-Host "Starting BFF on http://localhost:$env:SERVER_PORT ..."

& .\mvnw.cmd spring-boot:run
