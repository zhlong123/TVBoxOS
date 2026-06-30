# 将已编译 APK 复制到 release/apk/ 固定目录
# 用法: .\scripts\copy-release.ps1

$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$TvApp = Join-Path $Root 'TV-App'
$SrcRoot = Join-Path $TvApp 'app\build\outputs\apk'
$DestRoot = Join-Path $TvApp 'release\apk'

if (-not (Test-Path $SrcRoot)) {
    Write-Host "未找到构建输出: $SrcRoot"
    Write-Host "请先执行: .\scripts\build.ps1"
    exit 1
}

New-Item -ItemType Directory -Force -Path $DestRoot | Out-Null
$manifest = @()
$now = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'

Get-ChildItem $SrcRoot -Recurse -Filter '*.apk' | ForEach-Object {
    $rel = $_.FullName.Substring($SrcRoot.Length).TrimStart('\')
    $dest = Join-Path $DestRoot $rel
    $destDir = Split-Path $dest -Parent
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    Copy-Item $_.FullName $dest -Force
    $manifest += [pscustomobject]@{
        File = $rel.Replace('\', '/')
        SizeMB = [math]::Round($_.Length / 1MB, 2)
        Updated = $now
    }
    Write-Host "OK $($rel) ($([math]::Round($_.Length/1MB,2)) MB)"
}

$manifest | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $DestRoot 'manifest.json') -Encoding UTF8
Write-Host ""
Write-Host "输出目录: $DestRoot"
