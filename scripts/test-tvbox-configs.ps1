# 批量检测 tvboxConfig JSON 是否可下载、可解析、结构是否合理
# 用法: .\scripts\test-tvbox-configs.ps1
#       .\scripts\test-tvbox-configs.ps1 -AllRepo   # 扫描仓库全部 json（较慢）

param(
    [string]$RepoBase = "https://raw.githubusercontent.com/zhlong123/tvboxConfig/master",
    [switch]$AllRepo,
    [switch]$CheckSpider,
    [string]$OutFile = ""
)

$ErrorActionPreference = "Continue"

function Resolve-ConfigUrl {
    param([string]$BaseUrl, [string]$Ref)
    if ([string]::IsNullOrWhiteSpace($Ref)) { return $null }
    if ($Ref -match '^https?://') { return $Ref }
    $base = $BaseUrl -replace '/[^/]+$', ''
    if ($Ref.StartsWith('./')) { $Ref = $Ref.Substring(2) }
    return "$base/$Ref"
}

function Get-SpiderUrl {
    param([string]$SpiderField, [string]$ConfigUrl)
    if ([string]::IsNullOrWhiteSpace($SpiderField)) { return $null }
    $raw = ($SpiderField -split ';')[0].Trim()
    return Resolve-ConfigUrl -BaseUrl $ConfigUrl -Ref $raw
}

function Test-JsonConfig {
    param(
        [string]$Name,
        [string]$Url,
        [int]$Depth = 0
    )

    $result = [ordered]@{
        Name   = $Name
        Url    = $Url
        Depth  = $Depth
        Http   = "?"
        Parse  = "?"
        Type   = "?"
        Sites  = "-"
        Lives  = "-"
        Parses = "-"
        Spider = "-"
        Issues = @()
        Status = "FAIL"
    }

    try {
        $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 25 -MaximumRedirection 5
        $result.Http = [int]$resp.StatusCode
        if ($resp.StatusCode -ge 400) {
            $result.Issues += "HTTP $($resp.StatusCode)"
            return [pscustomobject]$result
        }
        $text = $resp.Content
        if ($text.Length -lt 2) {
            $result.Issues += "empty body"
            return [pscustomobject]$result
        }
        $result.Parse = "OK"
        $json = $text | ConvertFrom-Json
    } catch {
        $result.Http = "ERR"
        $result.Parse = "FAIL"
        $result.Issues += $_.Exception.Message
        return [pscustomobject]$result
    }

    if ($null -ne $json.urls -and $json.urls.Count -gt 0) {
        $result.Type = "index"
        $result.Sites = "urls=$($json.urls.Count)"
        $result.Status = "OK"
        return [pscustomobject]$result
    }

    $sites = @($json.sites)
    $lives = @($json.lives)
    $parses = @($json.parses)
    $result.Type = "full"
    $result.Sites = $sites.Count
    $result.Lives = $lives.Count
    $result.Parses = $parses.Count

    if ($sites.Count -eq 0 -and $lives.Count -eq 0) {
        $result.Issues += "no sites/lives"
    }

    if ($json.spider) {
        $spiderUrl = Get-SpiderUrl -SpiderField ([string]$json.spider) -ConfigUrl $Url
        $result.Spider = if ($spiderUrl) { $spiderUrl } else { "?" }
        if ($CheckSpider -and $spiderUrl) {
            try {
                $hr = Invoke-WebRequest -Uri $spiderUrl -Method Head -UseBasicParsing -TimeoutSec 20
                if ($hr.StatusCode -ge 400) { $result.Issues += "spider HTTP $($hr.StatusCode)" }
            } catch {
                try {
                    $gr = Invoke-WebRequest -Uri $spiderUrl -UseBasicParsing -TimeoutSec 20
                    if ($gr.RawContentLength -lt 1024) { $result.Issues += "spider too small" }
                } catch {
                    $result.Issues += "spider unreachable"
                }
            }
        }
    } else {
        $result.Spider = "(none)"
    }

    if ($result.Issues.Count -eq 0) {
        $result.Status = "OK"
    } elseif ($sites.Count -gt 0 -or $lives.Count -gt 0) {
        $result.Status = "WARN"
    }
    return [pscustomobject]$result
}

# ── 主入口配置（可作为 TVBox 配置地址）──
$mainEntries = @(
    @{ Name = "0707 多仓索引"; Path = "0707.json" },
    @{ Name = "jsm 家庭电视"; Path = "jsm.json" },
    @{ Name = "0821 大而全"; Path = "0821.json" },
    @{ Name = "0825 小而精"; Path = "0825.json" },
    @{ Name = "0826 FTY"; Path = "0826.json" },
    @{ Name = "0827 FM"; Path = "0827.json" },
    @{ Name = "js 聚合"; Path = "js.json" },
    @{ Name = "XYQ"; Path = "XYQ.json" },
    @{ Name = "fty"; Path = "fty.json" },
    @{ Name = "367"; Path = "367.json" },
    @{ Name = "9918"; Path = "9918.json" },
    @{ Name = "99188"; Path = "99188.json" },
    @{ Name = "dianshi"; Path = "dianshi.json" },
    @{ Name = "tools/jsm"; Path = "tools/jsm.json" },
    @{ Name = "tools/dianshi"; Path = "tools/dianshi.json" }
)

$results = @()
Write-Host "检测仓库: $RepoBase" -ForegroundColor Cyan
Write-Host ""

foreach ($entry in $mainEntries) {
    $url = "$RepoBase/$($entry.Path)"
    Write-Host "  $($entry.Name) ..." -NoNewline
    $r = Test-JsonConfig -Name $entry.Name -Url $url
    $results += $r
    Write-Host " $($r.Status)" -ForegroundColor $(if ($r.Status -eq 'OK') { 'Green' } elseif ($r.Status -eq 'WARN') { 'Yellow' } else { 'Red' })

    if ($r.Type -eq 'index' -and $r.Status -ne 'FAIL') {
        try {
            $idx = (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 25).Content | ConvertFrom-Json
            foreach ($u in $idx.urls) {
                $childName = if ($u.name) { [string]$u.name } else { [string]$u.url }
                $childUrl = Resolve-ConfigUrl -BaseUrl $url -Ref ([string]$u.url)
                Write-Host "    -> $childName ..." -NoNewline
                $cr = Test-JsonConfig -Name "  └ $childName" -Url $childUrl -Depth 1
                $results += $cr
                Write-Host " $($cr.Status)" -ForegroundColor $(if ($cr.Status -eq 'OK') { 'Green' } elseif ($cr.Status -eq 'WARN') { 'Yellow' } else { 'Red' })
            }
        } catch {
            Write-Host "    (展开多仓失败)" -ForegroundColor Red
        }
    }
}

if ($AllRepo) {
    Write-Host ""
    Write-Host "扫描仓库全部 .json ..." -ForegroundColor Cyan
    $tree = (Invoke-WebRequest -Uri "https://api.github.com/repos/zhlong123/tvboxConfig/git/trees/master?recursive=1" -UseBasicParsing).Content | ConvertFrom-Json
    $paths = $tree.tree | Where-Object { $_.path -match '\.json$' } | Select-Object -ExpandProperty path
    $skip = $mainEntries.Path + @("0707.json")
    foreach ($p in $paths) {
        if ($results.Url -match [regex]::Escape($p)) { continue }
        $url = "$RepoBase/$p"
        $r = Test-JsonConfig -Name $p -Url $url -Depth 2
        $results += $r
    }
}

Write-Host ""
Write-Host ("=" * 72)
$ok = @($results | Where-Object { $_.Status -eq 'OK' }).Count
$warn = @($results | Where-Object { $_.Status -eq 'WARN' }).Count
$fail = @($results | Where-Object { $_.Status -eq 'FAIL' }).Count
Write-Host "合计: $($results.Count)  OK=$ok  WARN=$warn  FAIL=$fail"
Write-Host ""

$results | Sort-Object Depth, Name | Format-Table Name, Status, Type, Sites, Lives, Parses, Http -AutoSize

if ($fail -gt 0 -or $warn -gt 0) {
    Write-Host "问题明细:" -ForegroundColor Yellow
    $results | Where-Object { $_.Status -ne 'OK' -and $_.Issues.Count -gt 0 } | ForEach-Object {
        Write-Host "  [$($_.Status)] $($_.Name)"
        $_.Issues | ForEach-Object { Write-Host "      - $_" }
    }
}

if ($OutFile) {
    $results | Export-Csv -Path $OutFile -NoTypeInformation -Encoding UTF8
    Write-Host "已写入 $OutFile"
}
