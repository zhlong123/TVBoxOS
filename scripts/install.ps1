# 从 release/apk 安装到已连接设备
# 用法: .\scripts\install.ps1 [-Flavor java64] [-Type release]

param(
    [ValidateSet('java', 'java32', 'java64', 'python', 'python32', 'python64')]
    [string]$Flavor = 'java64',
    [ValidateSet('debug', 'release')]
    [string]$Type = 'release'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$apk = Join-Path $Root "release\apk\$Flavor\$Type\TVBox_${Type}-${Flavor}.apk"

if (-not (Test-Path $apk)) {
    $apk = Join-Path $Root "app\build\outputs\apk\$Flavor\$Type\TVBox_${Type}-${Flavor}.apk"
}

if (-not (Test-Path $apk)) {
    Write-Host "APK 不存在: $apk"
    Write-Host "请先: .\scripts\build.ps1 -Flavor $Flavor -Type $Type"
    exit 1
}

Write-Host "Installing: $apk"
adb install -r $apk
