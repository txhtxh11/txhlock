param(
    [string]$Version = "1.9.34",
    [string]$ShareDir = "D:\share"
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $repo "apk\txhlock_v$Version.apk"
$guide = Join-Path $repo "docs\guide-v$Version.pdf"
$projectRoot = Split-Path -Parent (Split-Path -Parent $repo)
$vertical = Join-Path $projectRoot "每日壁纸-抖音竖屏指南-v$Version.pdf"

if (-not (Test-Path $apk)) { throw "找不到 APK：$apk" }
if (-not (Test-Path $guide)) { throw "找不到指南 PDF：$guide" }

New-Item -ItemType Directory -Force -Path $ShareDir | Out-Null

# 固定文件名：每次覆盖旧版本，豆包/用户只认这两个名字
Copy-Item $apk (Join-Path $ShareDir "txhlock.apk") -Force
Copy-Item $guide (Join-Path $ShareDir "每日壁纸-安装使用指南.pdf") -Force
if (Test-Path $vertical) {
    Copy-Item $vertical (Join-Path $ShareDir "每日壁纸-抖音竖屏指南.pdf") -Force
}

# 清掉旧的带版本号文件，避免误拿旧版
Get-ChildItem $ShareDir -File | Where-Object {
    $_.Name -match '^txhlock_v[0-9.]+\.apk$' -or
    $_.Name -match '^每日壁纸-.*-v[0-9.]+\.pdf$'
} | Remove-Item -Force

$hash = (Get-FileHash $apk -Algorithm MD5).Hash.ToLower()
$lines = @(
    "版本：v$Version",
    "APK：txhlock.apk（$((Get-Item $apk).Length) 字节）",
    "MD5：$hash",
    "指南：每日壁纸-安装使用指南.pdf",
    "更新时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
)
[System.IO.File]::WriteAllLines((Join-Path $ShareDir "版本.txt"), $lines, (New-Object System.Text.UTF8Encoding($true)))

Get-ChildItem $ShareDir -File | Select-Object Name, Length
