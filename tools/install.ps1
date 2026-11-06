# =====================================================================
#  txhlock 答题锁屏 · 一键安装器（Windows）
#
#  作用：自动找 adb → 自动在局域网里找电视 → 自动安装 APK →
#        自动打开无障碍保活 → 自动让电视保持常亮 → 启动应用
#
#  用法（最简单）：
#    双击 tools\一键安装.bat
#  或者：
#    powershell -ExecutionPolicy Bypass -File tools\install.ps1
#
#  可选参数：
#    -TvIp 192.168.50.139    电视已知 IP 时直接连，跳过扫描
#    -ApkPath D:\txhlock.apk 指定安装包路径
#    -WaitSeconds 300        找不到电视时等待多久（秒），默认 300
#    -NoLaunch               装完不自动打开应用
#
#  说明：不需要管理员权限；不会卸载旧版、不会改系统桌面。
# =====================================================================
param(
    [string]$TvIp = "",
    [string]$ApkPath = "",
    [int]$WaitSeconds = 300,
    [switch]$NoLaunch,
    [switch]$NoPause
)

$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch { }
try { chcp 65001 > $null } catch { }

$script:LogFile = Join-Path $env:TEMP "txhlock-install.log"
$script:Version = "1.9.19"
$script:Pkg = "com.kidslock"
$script:AccService = "com.kidslock/com.kidslock.BootAccessibility"

# 非交互运行（AI Agent / 输出被重定向）时不暂停，避免自动化流程卡住
$script:Interactive = $true
try { if ([Console]::IsInputRedirected) { $script:Interactive = $false } } catch { }
if ($NoPause) { $script:Interactive = $false }
function Wait-Exit {
    if ($script:Interactive) { Read-Host "  按回车键退出" }
}

function Log {
    param([string]$msg = "", [string]$color = "Gray")
    Write-Host $msg -ForegroundColor $color
    try { Add-Content -Path $script:LogFile -Value ("[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $msg) -Encoding UTF8 } catch { }
}

function Step {
    param([string]$msg)
    Log ""
    Log ("=" * 58) "DarkGray"
    Log ("  " + $msg) "Cyan"
    Log ("=" * 58) "DarkGray"
}

function Fail {
    param([string]$msg, [string]$hint = "")
    Log ""
    Log "  ✗ $msg" "Red"
    if ($hint -ne "") { Log "    $hint" "Yellow" }
    Log ""
    Log "  日志已保存：$script:LogFile" "DarkGray"
    Log ""
    Wait-Exit
    exit 1
}

function Download {
    param([string]$url, [string]$dest, [string]$label = "")
    if ($label -ne "") { Log "  正在下载 $label …" "Gray" }
    $dir = Split-Path -Parent $dest
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    try {
        Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing -TimeoutSec 180
        return (Test-Path $dest) -and ((Get-Item $dest).Length -gt 0)
    } catch {
        try {
            $wc = New-Object System.Net.WebClient
            $wc.Headers.Add("User-Agent", "Mozilla/5.0")
            $wc.DownloadFile($url, $dest)
            return (Test-Path $dest) -and ((Get-Item $dest).Length -gt 0)
        } catch {
            return $false
        }
    }
}

# ---------------------------------------------------------------------
# 0. 欢迎
# ---------------------------------------------------------------------
Log ""
Log "  ┌──────────────────────────────────────────────┐" "Cyan"
Log "  │   答题锁屏守护  ·  一键安装到电视  v$script:Version     │" "Cyan"
Log "  └──────────────────────────────────────────────┘" "Cyan"
Log ""
Log "  这个窗口会自动完成：找电视 → 装应用 → 开权限 → 打开应用" "Gray"
Log "  中途只在电视上点一次「允许」就行，其余不用管。" "Gray"

# ---------------------------------------------------------------------
# 1. 找 adb
# ---------------------------------------------------------------------
Step "第 1 步 / 共 5 步：准备 adb 工具"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$adbCandidates = @(
    (Join-Path $scriptDir "platform-tools\adb.exe"),
    (Join-Path $scriptDir "..\platform-tools\adb.exe"),
    (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
    (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk\platform-tools\adb.exe"),
    "C:\platform-tools\adb.exe",
    "C:\Android\platform-tools\adb.exe",
    (Join-Path $env:ProgramFiles "Android\platform-tools\adb.exe")
)
$adb = $null
foreach ($p in $adbCandidates) {
    if ($p -and (Test-Path $p)) { $adb = (Resolve-Path $p).Path; break }
}
if (-not $adb) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $adb = $cmd.Source }
}
if (-not $adb) {
    Log "  电脑上没有 adb，正在自动下载（约 8MB，国内可能慢一点）…" "Yellow"
    $zip = Join-Path $env:TEMP "txhlock-platform-tools.zip"
    $ok = Download "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" $zip "adb 工具"
    if ($ok) {
        $out = Join-Path $env:TEMP "txhlock-platform-tools"
        if (Test-Path $out) { Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue }
        try {
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $out)
            $candidate = Join-Path $out "platform-tools\adb.exe"
            if (Test-Path $candidate) { $adb = $candidate }
        } catch {
            Log "  解压失败：$($_.Exception.Message)" "Red"
        }
    }
}
if (-not $adb) {
    Log "  自动下载失败，尝试用 winget 安装…" "Yellow"
    try {
        & winget install --id Google.PlatformTools --accept-source-agreements --accept-package-agreements 2>&1 | Out-Null
        $cmd = Get-Command adb -ErrorAction SilentlyContinue
        if ($cmd) { $adb = $cmd.Source }
        if (-not $adb -and (Test-Path (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"))) {
            $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
        }
    } catch { }
}
if (-not $adb) {
    Fail "找不到 adb，也下载不下来。" "请连上网后重试；或手动安装：winget install Google.PlatformTools"
}
Log "  ✓ adb：$adb" "Green"
& $adb start-server 2>&1 | Out-Null

# ---------------------------------------------------------------------
# 2. 找安装包
# ---------------------------------------------------------------------
Step "第 2 步 / 共 5 步：准备安装包"

$apk = $null
$apkCandidates = @(
    $ApkPath,
    (Join-Path $scriptDir "txhlock_v$script:Version.apk"),
    (Join-Path $scriptDir "..\apk\txhlock_v$script:Version.apk"),
    (Join-Path $scriptDir "..\txhlock_v$script:Version.apk"),
    (Join-Path $env:TEMP "txhlock_v$script:Version.apk")
)
foreach ($p in $apkCandidates) {
    if ($p -and (Test-Path $p)) { $apk = (Resolve-Path $p).Path; break }
}
if (-not $apk) {
    Log "  本地没有安装包，正在从网上下载…" "Yellow"
    $dest = Join-Path $env:TEMP "txhlock_v$script:Version.apk"
    $urls = @(
        "https://cdn.jsdelivr.net/gh/txhtxh11/txhlock@v$script:Version/apk/txhlock_v$script:Version.apk",
        "https://ghfast.top/https://github.com/txhtxh11/txhlock/releases/download/v$script:Version/txhlock_v$script:Version.apk",
        "https://ghproxy.net/https://github.com/txhtxh11/txhlock/releases/download/v$script:Version/txhlock_v$script:Version.apk",
        "https://fastly.jsdelivr.net/gh/txhtxh11/txhlock@v$script:Version/apk/txhlock_v$script:Version.apk"
    )
    foreach ($u in $urls) {
        if (Download $u $dest "安装包") {
            if ((Get-Item $dest).Length -gt 500000) { $apk = $dest; break }
        }
    }
}
if (-not $apk) {
    Fail "下载安装包失败。" "请检查网络，或把 txhlock_v$script:Version.apk 放到本脚本旁边再运行。"
}
$apkSize = [math]::Round((Get-Item $apk).Length / 1MB, 2)
Log "  ✓ 安装包：$apk（$apkSize MB）" "Green"

# ---------------------------------------------------------------------
# 3. 找电视
# ---------------------------------------------------------------------
Step "第 3 步 / 共 5 步：在局域网里找电视"

function Get-DeviceModel {
    param([string]$serial)
    try {
        $m = (& $adb -s $serial shell getprop ro.product.model 2>$null | Out-String).Trim()
        if ($m -eq "") { return "Android 设备" }
        return $m
    } catch { return "Android 设备" }
}

function Test-AdbDevice {
    param([string]$serial)
    try {
        $out = (& $adb -s $serial shell "echo ok" 2>$null | Out-String)
        return $out -match "ok"
    } catch { return $false }
}

# 3.1 已经连上的设备
$found = @()
try {
    $lines = (& $adb devices 2>$null)
    foreach ($line in $lines) {
        if ($line -match "^\s*([0-9\.]+:5555)\s+device") {
            $found += $Matches[1]
        }
    }
} catch { }

# 3.2 指定了 IP
if ($TvIp -ne "") {
    Log "  连接指定电视 $TvIp …" "Gray"
    & $adb connect "$TvIp`:5555" 2>&1 | Out-Null
    Start-Sleep -Milliseconds 800
    if (Test-AdbDevice "$TvIp`:5555") { $found = @($found) + "$TvIp`:5555" }
}

# 3.3 扫描局域网 5555 端口
function Scan-Lan {
    $localIps = @()
    try {
        $localIps = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } |
            ForEach-Object { $_.IPAddress }
    } catch {
        try {
            $localIps = [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) |
                Where-Object { $_.AddressFamily -eq "InterNetwork" } |
                ForEach-Object { $_.IPAddressToString }
        } catch { }
    }
    # 兼容：GetHostAddresses 的 ToString
    $localIps = $localIps | ForEach-Object { "$_" } | Where-Object { $_ -match "^\d+\.\d+\.\d+\.\d+$" -and $_ -notlike "127.*" -and $_ -notlike "169.254.*" }
    if (-not $localIps -or $localIps.Count -eq 0) { return @() }

    $result = @()
    foreach ($ip in $localIps) {
        $parts = $ip.Split(".")
        if ($parts.Count -ne 4) { continue }
        $prefix = "$($parts[0]).$($parts[1]).$($parts[2])"
        Log "  扫描 $prefix.1-254 的 5555 端口 …" "Gray"
        $pending = @()
        for ($i = 1; $i -le 254; $i++) {
            $target = "$prefix.$i"
            if ($target -eq $ip) { continue }
            $client = New-Object System.Net.Sockets.TcpClient
            try {
                $ar = $client.BeginConnect($target, 5555, $null, $null)
                $pending += [pscustomobject]@{ IP = $target; Client = $client; AR = $ar }
            } catch { try { $client.Close() } catch { } }
        }
        Start-Sleep -Milliseconds 900
        foreach ($p in $pending) {
            try {
                if ($p.AR.AsyncWaitHandle.WaitOne(0) -and $p.Client.Connected) { $result += $p.IP }
            } catch { }
            try { $p.Client.Close() } catch { }
        }
    }
    return ($result | Select-Object -Unique)
}

if ($found.Count -eq 0) {
    $open = Scan-Lan
    foreach ($ip in $open) {
        Log "  发现开着 5555 的设备：$ip" "Gray"
        & $adb connect "$ip`:5555" 2>&1 | Out-Null
        Start-Sleep -Milliseconds 500
        if (Test-AdbDevice "$ip`:5555") { $found = @($found) + "$ip`:5555" }
    }
}

# 3.4 还是没找到：等用户在电视上打开 ADB
$waited = 0
while ($found.Count -eq 0 -and $waited -lt $WaitSeconds) {
    if ($waited -eq 0) {
        Log ""
        Log "  没有找到电视。请在电视上做这一件事（只做一次）：" "Yellow"
        Log ""
        Log "    1) 遥控器按「设置」→ 进「关于」" "White"
        Log "    2) 选中「产品型号」，连按 5~7 次 OK，直到提示「已进入开发者模式」" "White"
        Log "    3) 返回「设置」→「开发者选项」→ 打开「ADB 调试 / USB 调试」" "White"
        Log "    4) 等电视弹出「允许 USB 调试吗」→ 选「始终允许」→ 确定" "White"
        Log ""
        Log "  打开后不用动，这个窗口会自动发现电视（最多等 $WaitSeconds 秒）…" "Gray"
    }
    Start-Sleep -Seconds 5
    $waited += 5
    if ($waited % 20 -eq 0) { Log ("  还在等… 已等待 {0} 秒（剩 {1} 秒）" -f $waited, ($WaitSeconds - $waited)) "DarkGray" }

    if ($TvIp -ne "") {
        & $adb connect "$TvIp`:5555" 2>&1 | Out-Null
        if (Test-AdbDevice "$TvIp`:5555") { $found = @($found) + "$TvIp`:5555" }
    } elseif ($waited % 15 -eq 0) {
        $open = Scan-Lan
        foreach ($ip in $open) {
            & $adb connect "$ip`:5555" 2>&1 | Out-Null
            Start-Sleep -Milliseconds 300
            if (Test-AdbDevice "$ip`:5555") { $found = @($found) + "$ip`:5555" }
        }
    }
    $found = @($found | Select-Object -Unique)
}

if ($found.Count -eq 0) {
    Fail "没有找到电视。" "请确认电脑和电视连的是同一个 WiFi/路由器，然后在电视上打开 ADB 调试后重新运行。"
}

$found = @($found | Select-Object -Unique)

# 3.5 找到多台时让用户选
$target = $null
if ($found.Count -eq 1) {
    $target = $found[0]
} elseif (-not $script:Interactive) {
    # 非交互（AI 自动执行）：优先选小米/电视，否则取第一台
    $target = $found[0]
    foreach ($f in $found) {
        $m = Get-DeviceModel $f
        if ($m -match "TV|MiTV|Xiaomi|小米|Coocaa|Skyworth|Hisense|TCL|Sony") { $target = $f; break }
    }
    Log "  找到多台设备，自动选择：$target" "Yellow"
} else {
    Log ""
    Log "  找到多台设备，请选择要安装到哪一台：" "Yellow"
    for ($i = 0; $i -lt $found.Count; $i++) {
        $m = Get-DeviceModel $found[$i]
        Log ("    [{0}] {1}  （{2}）" -f ($i + 1), $m, $found[$i]) "White"
    }
    $choice = Read-Host "  输入编号后回车（默认 1）"
    $idx = 0
    if ($choice -match "^\d+$") { $idx = [int]$choice - 1 }
    if ($idx -lt 0 -or $idx -ge $found.Count) { $idx = 0 }
    $target = $found[$idx]
}

$model = Get-DeviceModel $target
$sdk = ""
$rel = ""
try {
    $sdk = (& $adb -s $target shell getprop ro.build.version.sdk 2>$null | Out-String).Trim()
    $rel = (& $adb -s $target shell getprop ro.build.version.release 2>$null | Out-String).Trim()
} catch { }
Log "  ✓ 已连接电视：$model  （Android $rel / API $sdk）  $target" "Green"

# ---------------------------------------------------------------------
# 4. 安装
# ---------------------------------------------------------------------
Step "第 4 步 / 共 5 步：安装到电视"

Log "  正在安装，电视上可能要等 10~30 秒…" "Gray"
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
$installOut = (& $adb -s $target install -r $apk 2>&1 | Out-String)
$ErrorActionPreference = $prevEap
Log ("  " + $installOut.Trim().Replace("`n", "`n  ")) "DarkGray"

if ($installOut -match "Success") {
    Log "  ✓ 安装成功" "Green"
} elseif ($installOut -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match") {
    Log ""
    Log "  ✗ 电视上已经装了一个「签名不同」的旧版，不能直接覆盖。" "Red"
    Log "    为了不丢你的设置，安装器不会自动卸载。请二选一：" "Yellow"
    Log "      A. 在电视上：设置 → 应用 → 每日壁纸/答题锁屏 → 卸载，然后重新运行本安装器" "White"
    Log "      B. 让 AI 帮你执行：adb -s $target uninstall com.kidslock  （会清空答题记录和密码）" "White"
    Wait-Exit
    exit 1
} elseif ($installOut -match "INSTALL_FAILED_INSUFFICIENT_STORAGE") {
    Fail "电视存储空间不足。" "在电视上删掉几个不用的应用再试。"
} elseif ($installOut -match "INSTALL_FAILED_VERSION_DOWNGRADE") {
    Fail "电视上已装的版本比这个安装包还新。" "请用最新版安装包，或先在电视上卸载旧版。"
} else {
    Fail "安装失败。" "把上面的报错发给 AI，或检查电视是否弹出了「允许安装」的窗口。"
}

$ver = ""
try {
    $pkgDump = (& $adb -s $target shell "dumpsys package com.kidslock" 2>$null | Out-String)
    if ($pkgDump -match "versionName=([^\s]+)") { $ver = $Matches[1] }
} catch { }
Log "  ✓ 电视上当前版本：$ver" "Green"

# ---------------------------------------------------------------------
# 5. 装完配置
# ---------------------------------------------------------------------
Step "第 5 步 / 共 5 步：打开权限 + 保持常亮"

# 5.1 无障碍保活（防主页键、开机自启都靠它）
try {
    $cur = (& $adb -s $target shell "settings get secure enabled_accessibility_services" 2>$null | Out-String).Trim()
    if ($cur -eq "null") { $cur = "" }
    if ($cur -notmatch [regex]::Escape($script:AccService)) {
        $new = if ([string]::IsNullOrWhiteSpace($cur)) { $script:AccService } else { "$cur`:$script:AccService" }
        & $adb -s $target shell "settings put secure enabled_accessibility_services '$new'" 2>&1 | Out-Null
    }
    & $adb -s $target shell "settings put secure accessibility_enabled 1" 2>&1 | Out-Null
    Start-Sleep -Milliseconds 2000
    $now = (& $adb -s $target shell "settings get secure enabled_accessibility_services" 2>$null | Out-String).Trim()
    $dump = (& $adb -s $target shell "dumpsys accessibility" 2>$null | Out-String)
    # 应用已改名「每日壁纸」，服务标签也会跟着变，这里三种都认
    if ($now -match "com\.kidslock" -and ($dump -match "kidslock|答题锁屏|每日壁纸")) {
        Log "  ✓ 无障碍保活：已开启（主页键拦截 + 开机自启）" "Green"
    } else {
        # MIUI 刚覆盖安装后绑定慢，隔 2 秒再试一次
        & $adb -s $target shell "settings put secure accessibility_enabled 1" 2>&1 | Out-Null
        Start-Sleep -Milliseconds 2500
        $dump2 = (& $adb -s $target shell "dumpsys accessibility" 2>$null | Out-String)
        if ($dump2 -match "kidslock|答题锁屏|每日壁纸") {
            Log "  ✓ 无障碍保活：已开启（重试后绑定成功）" "Green"
        } else {
            Log "  ! 无障碍保活没自动打开，需要在电视上手动开一次" "Yellow"
            Log "    设置 → 无障碍 → 每日壁纸 → 打开" "White"
        }
    }
} catch {
    Log "  ! 无障碍设置失败：$($_.Exception.Message)" "Yellow"
}

# 5.1.5 悬浮层权限（Android 6+ 需要在系统里授权；4.3 安装即授予）
try {
    $sdkNum = 0
    [int]::TryParse($sdk.Trim(), [ref]$sdkNum) | Out-Null
    if ($sdkNum -gt 0 -and $sdkNum -lt 23) {
        Log "  ✓ 悬浮层权限：Android 4.3~5.1 安装即授权，无需额外操作" "Green"
    } else {
        & $adb -s $target shell "appops set com.kidslock SYSTEM_ALERT_WINDOW allow" 2>&1 | Out-Null
        Start-Sleep -Milliseconds 500
        $op = (& $adb -s $target shell "appops get com.kidslock SYSTEM_ALERT_WINDOW" 2>$null | Out-String)
        if ($op -match "allow") {
            Log "  ✓ 悬浮层权限：已授权（主页键切桌面会被直接盖住）" "Green"
        } else {
            Log "  · 悬浮层权限：请在电视上手动允许「显示在其他应用上层」" "Yellow"
            Log "    设置 → 应用 → 每日壁纸/答题锁屏 → 特殊权限 / 悬浮窗" "White"
        }
    }
} catch { }

# 5.2 电视保持常亮（装好后不黑屏，ADB 也不会因为待机掉线）
try {
    & $adb -s $target shell "settings put system screen_off_timeout 2147483647" 2>&1 | Out-Null
    & $adb -s $target shell "svc power stayon true" 2>&1 | Out-Null
    & $adb -s $target shell "input keyevent KEYCODE_WAKEUP" 2>&1 | Out-Null
    Log "  ✓ 已设置电视保持常亮" "Green"
} catch { }

# 5.3 启动应用
if (-not $NoLaunch) {
    try {
        & $adb -s $target shell "am start -n com.kidslock/.LauncherWallpaper" 2>&1 | Out-Null
        Log "  ✓ 已在电视上打开应用（桌面名：答题锁屏 / 每日壁纸）" "Green"
    } catch {
        Log "  ! 没能在电视上自动打开，请到电视桌面点「答题锁屏」图标" "Yellow"
    }
}

# ---------------------------------------------------------------------
# 完成
# ---------------------------------------------------------------------
Log ""
Log "  ┌──────────────────────────────────────────────┐" "Green"
Log "  │                 安装完成！                   │" "Green"
Log "  └──────────────────────────────────────────────┘" "Green"
Log ""
Log "  接下来在电视上还要做 3 件小事（都是一次性的）：" "Yellow"
Log ""
Log "    1. 打开桌面上的「每日壁纸」；进家长设置要长按菜单键 10 秒 + 密码 1234" "White"
Log "    2. 到 设置 → 应用 → 每日壁纸/答题锁屏 → 打开「自启动」（小米电视必须开）" "White"
Log "    3. 安全起见，回到 设置 → 开发者选项 → 关掉「ADB 调试」" "White"
Log ""
Log "  验证：在设置页点「立即锁定测试」，答对 3 题能解锁就说明一切正常。" "Gray"
Log ""
Log "  日志：$script:LogFile" "DarkGray"
Log ""
Read-Host "  按回车键结束"
