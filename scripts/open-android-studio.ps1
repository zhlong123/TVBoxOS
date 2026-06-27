# 用 Android Studio 打开项目（若已安装）
$studioPaths = @(
    "${env:ProgramFiles}\Android\Android Studio\bin\studio64.exe",
    "${env:LocalAppData}\Programs\Android Studio\bin\studio64.exe",
    "D:\Tools\Android\Android Studio\bin\studio64.exe"
)

$Root = Split-Path $PSScriptRoot -Parent
$studio = $studioPaths | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $studio) {
    Write-Host "未找到 Android Studio，请手动打开目录: $Root"
    exit 1
}

Write-Host "Opening: $Root"
Start-Process -FilePath $studio -ArgumentList $Root
