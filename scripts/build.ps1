# TVBox 本地构建脚本
# 用法: .\scripts\build.ps1 [-Flavor java64] [-Type release]

param(
    [ValidateSet('java', 'java32', 'java64', 'python', 'python32', 'python64')]
    [string]$Flavor = 'java64',
    [ValidateSet('debug', 'release')]
    [string]$Type = 'release',
    [bool]$CopyToRelease = $true
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$Sdk = 'D:\Tools\Android\SDK'

$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk

Push-Location $Root
try {
    $task = "assemble$($Flavor.Substring(0,1).ToUpper() + $Flavor.Substring(1))$($Type.Substring(0,1).ToUpper() + $Type.Substring(1))"
    Write-Host "Building: $task"
    & .\gradlew.bat $task --no-daemon
    $apkDir = Join-Path $Root "app\build\outputs\apk\$Flavor\$Type"
    if (Test-Path $apkDir) {
        Get-ChildItem $apkDir -Filter '*.apk' | ForEach-Object {
            Write-Host "APK: $($_.FullName) ($([math]::Round($_.Length/1MB, 2)) MB)"
        }
    }
    if ($CopyToRelease) {
        & (Join-Path $PSScriptRoot 'copy-release.ps1')
    }
} finally {
    Pop-Location
}
