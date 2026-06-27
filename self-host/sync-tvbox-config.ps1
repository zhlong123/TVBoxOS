# 同步 GitHub 配置仓 zhlong123/tvboxConfig 到 self-host/tvboxConfig
# 用法: .\self-host\sync-tvbox-config.ps1

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$Target = Join-Path $Root "tvboxConfig"
$Repo = "https://github.com/zhlong123/tvboxConfig.git"

Write-Host "目标目录: $Target"

if (Test-Path (Join-Path $Target ".git")) {
    Write-Host "已存在仓库，执行 git pull ..."
    Push-Location $Target
    try {
        git pull --ff-only origin master
        if ($LASTEXITCODE -ne 0) {
            git pull --ff-only origin main
        }
    } finally {
        Pop-Location
    }
} elseif (Test-Path $Target) {
    Write-Host "目录已存在但非 git 仓库，请先删除后重试: $Target"
    exit 1
} else {
    Write-Host "首次克隆: $Repo"
    git clone --depth 1 $Repo $Target
}

Write-Host ""
Write-Host "同步完成。启动服务后可用:"
Write-Host "  多仓: http://<本机IP>:9978/config/index.json"
Write-Host "  单线: http://<本机IP>:9978/tvboxConfig/jsm.json"
Write-Host ""
Write-Host "也可直接用 GitHub Raw（无需本地服务）:"
Write-Host "  https://raw.githubusercontent.com/zhlong123/tvboxConfig/master/0707.json"
Write-Host "  https://raw.githubusercontent.com/zhlong123/tvboxConfig/master/jsm.json"
