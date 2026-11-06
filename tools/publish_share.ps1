param(
    [string]$Version = "1.9.34",
    [string]$ShareDir = "D:\share"
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $repo "apk\txhlock_v$Version.apk"
$guide = Join-Path $repo "docs\guide-v$Version.pdf"

if (-not (Test-Path $apk)) { throw "找不到 APK：$apk" }
if (-not (Test-Path $guide)) { throw "找不到指南 PDF：$guide" }

$full = [System.IO.Path]::GetFullPath($ShareDir)
if ($full -match '^[A-Za-z]:\\$') { throw "拒绝清空盘符根目录：$full" }

New-Item -ItemType Directory -Force -Path $ShareDir | Out-Null

# 每次先清空，只放最新 APK + 安装指南两个文件
Get-ChildItem -LiteralPath $ShareDir -Force | Remove-Item -Recurse -Force

Copy-Item $apk (Join-Path $ShareDir "txhlock_v$Version.apk") -Force
Copy-Item $guide (Join-Path $ShareDir "每日壁纸-安装使用指南-v$Version.pdf") -Force

Get-ChildItem -LiteralPath $ShareDir -File | Select-Object Name, Length
