<#
.SYNOPSIS
    Guide and setup helper for configuring a GitHub Actions Self-Hosted Runner on this machine.

.DESCRIPTION
    Running GitHub Actions locally enables:
    1. Direct access to the internal PostgreSQL database (192.168.155.22:5443) without VPN tunnels.
    2. Real desktop Chrome browser visibility during automated CI/CD runs.
    3. Direct file export into C:\Users\Hasibur Rahman\Downloads.
#>

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "         GITHUB ACTIONS SELF-HOSTED RUNNER SETUP GUIDE                   " -ForegroundColor Yellow
Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "To link this machine to your GitHub repository (https://github.com/hr9294/OMS-WEB):" -ForegroundColor White
Write-Host ""
Write-Host "1. Open your browser and go to:" -ForegroundColor Green
Write-Host "   https://github.com/hr9294/OMS-WEB/settings/actions/runners/new?arch=x64&os=win" -ForegroundColor White
Write-Host ""
Write-Host "2. Run the commands shown on GitHub in an Administrative PowerShell window:" -ForegroundColor Green
Write-Host "   Example:" -ForegroundColor Gray
Write-Host "     mkdir C:\actions-runner ; cd C:\actions-runner" -ForegroundColor Gray
Write-Host "     Invoke-WebRequest -Uri <DOWNLOAD_URL> -OutFile actions-runner.zip" -ForegroundColor Gray
Write-Host "     Add-Type -AssemblyName System.IO.Compression.FileSystem ; [System.IO.Compression.ZipFile]::ExtractToDirectory(`$PWD\actions-runner.zip, `$PWD)" -ForegroundColor Gray
Write-Host "     .\config.cmd --url https://github.com/hr9294/OMS-WEB --token <YOUR_TOKEN>" -ForegroundColor Gray
Write-Host "     .\run.cmd" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Once running, in GitHub Actions you can run workflows with:" -ForegroundColor Green
Write-Host "   runs-on: self-hosted" -ForegroundColor Yellow
Write-Host ""
Write-Host "==========================================================================" -ForegroundColor Cyan
