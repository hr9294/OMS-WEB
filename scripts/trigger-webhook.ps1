<#
.SYNOPSIS
    Test script to simulate sending a Repository Dispatch trigger to GitHub Actions.

.DESCRIPTION
    This demonstrates what the Developer's pipeline executes to trigger your testing code.
    Usage:
      $env:QA_TRIGGER_TOKEN = "your_token_here"
      .\scripts\trigger-webhook.ps1
    Or:
      .\scripts\trigger-webhook.ps1 -Token "your_token_here"
#>

param(
    [Parameter(Mandatory=$false)]
    [string]$Token = $env:QA_TRIGGER_TOKEN,

    [string]$Author = "QA-Test-Engineer",
    [string]$CommitSha = "dev-build-101",
    [string]$Environment = "https://devoms.quantbd.com"
)

if (-not $Token) {
    Write-Host "[NOTE] No token provided. Pass -Token or set `$env:QA_TRIGGER_TOKEN" -ForegroundColor Yellow
    Write-Host "Example: .\scripts\trigger-webhook.ps1 -Token ghp_YOUR_TOKEN" -ForegroundColor Gray
    exit 0
}

$uri = "https://api.github.com/repos/hr9294/OMS-WEB/dispatches"

$headers = @{
    "Accept"        = "application/vnd.github.v3+json"
    "Authorization" = "token $Token"
    "User-Agent"    = "PowerShell-Trigger-Client"
}

$bodyObj = @{
    event_type     = "app_deployed"
    client_payload = @{
        commit      = $CommitSha
        author      = $Author
        environment = $Environment
        repository  = "OMS-Development-Repo"
    }
}

$bodyJson = $bodyObj | ConvertTo-Json -Depth 4

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "        TRIGGERING OMS AUTOMATION VIA GITHUB DISPATCH WEBHOOK            " -ForegroundColor Yellow
Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "Endpoint: $uri" -ForegroundColor Gray
Write-Host "Payload:  $bodyJson" -ForegroundColor White
Write-Host ""

try {
    $response = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -Body $bodyJson
    Write-Host "[SUCCESS 204 No Content] Webhook delivered to GitHub Actions!" -ForegroundColor Green
    Write-Host "Your testing pipeline (trade-automation.yml) is now running on GitHub!" -ForegroundColor Yellow
    Write-Host "Check live run: https://github.com/hr9294/OMS-WEB/actions" -ForegroundColor Cyan
} catch {
    Write-Host "[ERROR] Could not send webhook: $($_.Exception.Message)" -ForegroundColor Red
}
