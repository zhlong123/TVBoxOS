# 启动自建接口静态文件服务（默认端口 9978）
# 用法: .\self-host\start-server.ps1 [-Port 9978] [-BindAll]

param(
    [int]$Port = 9978,
    [bool]$BindAll = $true
)

$Root = $PSScriptRoot
$bind = if ($BindAll) { "0.0.0.0" } else { "127.0.0.1" }

Write-Host "服务目录: $Root"
Write-Host "监听: http://${bind}:$Port/"
Write-Host ""
Write-Host "TVBox 配置地址（本机）:"
Write-Host "  多仓: http://127.0.0.1:$Port/config/index.json"
Write-Host "  单接口: http://127.0.0.1:$Port/config/main.json"
Write-Host "  tvboxConfig: http://127.0.0.1:$Port/tvboxConfig/jsm.json"
Write-Host ""
Write-Host "首次使用 tvboxConfig 请先: .\self-host\sync-tvbox-config.ps1"
Write-Host "GitHub 直连: https://raw.githubusercontent.com/zhlong123/tvboxConfig/master/jsm.json"
Write-Host "局域网请把 127.0.0.1 换成本机 IP（ipconfig 查看）"
Write-Host "按 Ctrl+C 停止"
Write-Host ""

$py310 = "C:\Users\Administrator\AppData\Local\Programs\Python\Python310\python.exe"
$py312 = "C:\Users\Administrator\AppData\Local\Programs\Python\Python312\python.exe"
$python = if (Test-Path $py310) { $py310 } elseif (Test-Path $py312) { $py312 } else { "python" }

Push-Location $Root
try {
    & $python -m http.server $Port --bind $bind
} finally {
    Pop-Location
}
