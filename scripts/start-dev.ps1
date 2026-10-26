# Starts the Spring Boot API in a new window, waits until it is healthy, then runs the Vite frontend.
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$backendCmd = "Set-Location '$repoRoot\backend'; .\mvnw.cmd spring-boot:run"
Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendCmd | Out-Null

Write-Host "Waiting for the library API on http://localhost:8080 ..."
$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 2 | Out-Null
        Write-Host "API is up."
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}

Set-Location $repoRoot
npx vite
