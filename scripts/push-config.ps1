# Push TVBox config URL to device/emulator and reload app.
# Usage:
#   .\scripts\push-config.ps1
#   .\scripts\push-config.ps1 -ConfigUrl "https://raw.githubusercontent.com/zhlong123/tvboxConfig/master/0707.json"
#   .\scripts\push-config.ps1 -UseLocalHost

param(
    [string]$Device = "emulator-5556",
    [string]$ConfigUrl = "https://raw.githubusercontent.com/zhlong123/tvboxConfig/master/jsm.json",
    [int]$ForwardPort = 9988,
    [switch]$UseLocalHost,
    [int]$LocalPort = 9978,
    [string]$Package = "com.github.tvbox.osc.jun"
)

$ErrorActionPreference = "Stop"
$adb = "D:\Tools\Android\SDK\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    throw "adb not found: $adb"
}

function Get-LanIp {
    $ip = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' -and $_.PrefixOrigin -ne 'WellKnown' } |
        Select-Object -First 1).IPAddress
    if (-not $ip) {
        $line = ipconfig | Select-String "IPv4" | Select-Object -First 1
        if ($line -match '(\d+\.\d+\.\d+\.\d+)') { $ip = $Matches[1] }
    }
    return $ip
}

if ($UseLocalHost) {
    $lan = Get-LanIp
    if (-not $lan) { throw "Cannot detect LAN IP; pass -ConfigUrl manually." }
    $ConfigUrl = "http://${lan}:${LocalPort}/tvboxConfig/jsm.json"
    Write-Host "Local config URL: $ConfigUrl"
    Write-Host "Starting self-host on port $LocalPort ..."
    Start-Process -WindowStyle Hidden -FilePath "powershell.exe" -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSScriptRoot\..\self-host\start-server.ps1`" -Port $LocalPort"
    Start-Sleep -Seconds 2
}

Write-Host "Device: $Device"
Write-Host "Config: $ConfigUrl"

& $adb -s $Device shell am force-stop $Package | Out-Null
& $adb -s $Device shell am start -n "$Package/com.github.tvbox.osc.ui.activity.HomeActivity" | Out-Null
Start-Sleep -Seconds 4

& $adb -s $Device forward "tcp:$ForwardPort" tcp:9978 | Out-Null

$body = "do=api&url=$([uri]::EscapeDataString($ConfigUrl))"
try {
    $resp = Invoke-WebRequest -Uri "http://127.0.0.1:$ForwardPort/action" -Method POST -Body $body -ContentType "application/x-www-form-urlencoded" -TimeoutSec 15
    Write-Host "Push response: $($resp.Content)"
}
catch {
    throw "Push failed (is TVBox running with remote server on 9978?): $_"
}

Write-Host "Waiting for app restart and config load ..."
Start-Sleep -Seconds 12

& $adb -s $Device logcat -d -t 300 2>&1 | Select-String -Pattern "echo-load jar|load-jar-success|jar Loader threw|拉取配置失败|配置解析失败" | Select-Object -Last 20 | ForEach-Object { Write-Host $_.Line }

$logText = (& $adb -s $Device logcat -d -t 300 2>&1) -join "`n"
if ($logText -match "load-jar-success") {
    Write-Host "OK: jar loaded successfully." -ForegroundColor Green
}
elseif ($logText -match "拉取配置失败|配置解析失败|jar Loader threw") {
    Write-Host "WARN: config load may have failed; check app settings." -ForegroundColor Yellow
    exit 1
}
else {
    Write-Host "Push sent; verify sources on home screen." -ForegroundColor Green
}
