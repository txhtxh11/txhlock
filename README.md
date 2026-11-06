# 每日壁纸（答题锁屏）· 小米电视版 v1.9.21

> **用户只需要把这一句话发给豆包（电脑版 / Agent 模式）：**
>
> **进 https://github.com/txhtxh11/txhlock 仓库阅读 README，并按指导执行下载安装和为用户给予使用指南。**

---

## 豆包电脑版：请按这个顺序执行

你是一名电脑上的安装助手。目标：帮用户把电视装上 v1.9.21、把该开的权限开好，最后把使用指南交给用户。

### 第 0 步：先确认三件事

1. 电视是 **Android 4.3 及以上**（小米电视 2 / MiTV2-49 已验证）。
2. 电脑和电视连在**同一个路由器 / 同一个局域网**。
3. 问用户电视的 IP（电视：设置 → 关于 / 网络信息 → 状态信息里看 IPv4，形如 `192.168.50.139`）。

### 第 1 步：下载安装器

优先用国内镜像（任选一个能下载的）：

```powershell
# 方式 A：ghfast 镜像
Invoke-WebRequest "https://ghfast.top/https://raw.githubusercontent.com/txhtxh11/txhlock/v1.9.21/tools/install.ps1" -OutFile "$env:TEMP\install.ps1"

# 方式 B：jsDelivr
Invoke-WebRequest "https://cdn.jsdelivr.net/gh/txhtxh11/txhlock@v1.9.21/tools/install.ps1" -OutFile "$env:TEMP\install.ps1"
```

### 第 2 步：安装（一条命令）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:TEMP\install.ps1" -TvIp 192.168.50.139 -NoPause
```

把 `192.168.50.139` 换成用户电视的真实 IP；不知道 IP 就去掉 `-TvIp`，脚本会自动扫局域网。

脚本会自动完成：

1. 找到 / 下载 adb；
2. 连接电视、安装 `txhlock_v1.9.21.apk`；
3. 打开「每日壁纸」的无障碍保活；
4. 授权悬浮层、设置屏幕常亮；
5. 启动应用。

如果脚本提示连不上电视：让用户在电视上打开 ADB —— 设置 → 关于 / 系统信息 → 光标移到「产品型号」连按 OK 5~7 次 → 返回 设置 → 开发者选项 → 打开 **ADB 调试**，然后重跑上面的命令。

### 第 3 步：检查是否成功

```powershell
# 如果 adb 不在 PATH：安装器准备工作目录下的 platform-tools\adb.exe
adb -s 192.168.50.139:5555 shell "dumpsys package com.kidslock | grep versionName"
# 期望：versionName=1.9.20

adb -s 192.168.50.139:5555 shell "dumpsys accessibility"
# 期望：Service[label=每日壁纸 ...]
```

电视桌面出现 **「每日壁纸」** 图标（不是「答题锁屏」）即安装成功；如果还是旧名字/旧图标，让用户重启一次电视。

### 第 4 步：带用户做首次设置

请让用户拿着遥控器，按下面做（你在旁边解释即可）：

1. 桌面点「每日壁纸」→ 看到壁纸。
2. **按住菜单键约 10 秒**（最后 2 秒有倒计时）→ 输入家长密码 **1234**。
3. 确认「监控：已开启」。
4. 看「无障碍」：如果是红色「未开启」，点「去系统里打开无障碍（当前：未开启）」→ 在系统设置里打开「每日壁纸」的无障碍开关。
5. 看「自启动」：
   - 显示 **「自启动：已开启（系统里已允许，无需操作）」** → 不用点；
   - 显示 **「未开启 / 未确认」** → 点按钮去应用详情页，把自启动打开。
6. 建议把家长密码改成用户自己的 4~8 位数字（设置 → 修改家长密码）。
7. 按用户需求设置：单次连续观看（默认 20 分钟）、每日上限（0=不限）、连续答对（默认 3 题）。

### 第 5 步：把使用指南交给用户

- **完整图文指南（PDF）**：`https://raw.githubusercontent.com/txhtxh11/txhlock/master/docs/guide-v1.9.21.pdf`
- 把链接发给用户，或直接把下面「给用户的简短使用指南」念/发给用户。

### 第 6 步：告诉用户以后怎么更新

更新只需重跑一次安装器；**小米电视覆盖安装后可能会把无障碍自动关掉**，更新完回「家长设置 → 防绕过」看一眼，红色「未开启」就点一下重开。

---

## 给用户的简短使用指南（豆包可照着念）

1. **孩子看电视**：正常看，后台会计时；看满设定时长自动弹出答题，**连续答对 3 题**才能继续看。
2. **家长进设置**：在「每日壁纸」界面**按住菜单键 10 秒** → 输入家长密码（默认 `1234`）。
3. **手机扫码设置**：长按 10 秒后的验证页右侧有二维码；手机连**同一个家庭 WiFi** 扫码 → 输入同一个家长密码 → 可改设置、**立刻锁定 / 立刻解锁**。
4. **换壁纸**：在壁纸界面**短按**菜单键，不是进设置。
5. **卸载**：电视 设置 → 应用 → 每日壁纸 → 卸载。

---

## 下载

| 文件 | 地址 |
| --- | --- |
| APK（jsDelivr） | https://cdn.jsdelivr.net/gh/txhtxh11/txhlock@v1.9.21/apk/txhlock_v1.9.21.apk |
| APK（ghfast） | https://ghfast.top/https://github.com/txhtxh11/txhlock/releases/download/v1.9.21/txhlock_v1.9.21.apk |
| APK（ghproxy） | https://ghproxy.net/https://github.com/txhtxh11/txhlock/releases/download/v1.9.21/txhlock_v1.9.21.apk |
| 安装器脚本 | https://ghfast.top/https://raw.githubusercontent.com/txhtxh11/txhlock/v1.9.21/tools/install.ps1 |
| 图文指南 PDF | https://raw.githubusercontent.com/txhtxh11/txhlock/master/docs/guide-v1.9.21.pdf |

- 文件大小：**4,413,934 字节**
- MD5：`ed05dcdc08736ca0ece24c1fde417a86`
- 适用：Android 4.3（API 18）及以上，已在小米电视 2（MiTV2-49 / 1080p）实机验证

---

## 安装（三种方式，任选一种）

### 方式一：一键安装器（推荐）

1. 下载 `tools/install.ps1`（或把仓库里的 `安装器` 文件夹拷到电脑）。
2. 双击 `一键安装.bat`，或运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\install.ps1 -TvIp 192.168.50.139
```

3. 脚本自动找 adb → 找电视 → 装 APK → 开无障碍 → 授权悬浮层 → 常亮 → 启动。

### 方式二：U 盘

把 `txhlock_v1.9.21.apk` 拷到 U 盘 → 插电视 → 电视「文件管理」里找到 APK → 安装 → 手动打开无障碍。

### 方式三：ADB 命令

```bash
adb connect 192.168.50.139:5555
adb install -r txhlock_v1.9.21.apk
adb shell settings put secure enabled_accessibility_services com.kidslock/com.kidslock.BootAccessibility
adb shell settings put secure accessibility_enabled 1
adb shell am start -n com.kidslock/.LauncherWallpaper
```

---

## 首次设置（装完必做）

1. 打开「每日壁纸」→ 长按菜单键 10 秒 → 密码 `1234`。
2. 确认 **监控：已开启**。
3. 打开 **无障碍**（红色未开启时点按钮去系统设置打开）。
4. **悬浮层**：显示绿色「已开启（系统自动授权，无需操作）」即可，不用点。
5. **自启动**：显示绿色「已开启（无需操作）」就不用管；显示「未开启 / 未确认」才点按钮去打开。
6. 修改家长密码；按孩子情况设置单次/每日/连对题数。

---

## 日常使用

| 操作 | 结果 |
| --- | --- |
| 打开桌面「每日壁纸」 | 进入壁纸页（不验证） |
| 看满设定时长 | 自动锁屏，弹出答题 |
| 连续答对 N 题 | 解锁，重新计时 |
| 锁屏页按菜单键 | 输入家长密码，直接解锁 |
| 壁纸页短按菜单键 | 壁纸设置（每日更新 / 换一张 / 关闭） |
| 壁纸页长按菜单键 10 秒 | 家长密码 → 家长设置 |
| 手机扫码（验证页右侧） | 同一 WiFi 下改设置、立刻锁定 / 解锁 |

### 手机扫码设置

1. 电视长按菜单键 10 秒 → 验证页右侧二维码 + 地址（`http://电视IP:8765/`）。
2. 手机连同一个家庭 WiFi → 扫码 / 输地址 → 输入家长密码。
3. 可改监控、单次/每日/长关机、连对题数、长按秒数、开机先答题、家长密码；可「立刻锁定 / 立刻解锁」。
4. 只限局域网：手机用 4G/5G 打不开；连续错 5 次密码锁 5 分钟；10 分钟无人访问自动关闭。

---

## 关机 / 重启规则

| 设置 | 场景 | 结果 |
| --- | --- | --- |
| 开机先答题 = 开 | 短关机 / 重启 | 一律先答题 |
| 开机先答题 = 关 | 短关机 / 重启，之前未锁定 | 不锁定，接着上次计时 |
| 开机先答题 = 关 | 短关机 / 重启，之前已锁定 | 仍然锁定 |
| 任意 | 长关机（超过「长关机」阈值，未跨夜） | 清本次计时，保留今日累计 |
| 任意 | 跨夜 | 单次计时、锁定、今日额度全部清零 |

「长关机」阈值在 设置 → 时间 → 长关机重置（默认 30 分钟，0 = 不重置）。

---

## 题库

- 内置 **2297 道**：人教版 / 北师大 / 苏教版 / 外研版 / 译林版 / 通用，按年级 + 教材版本出题。
- 本地题库：放到电视 `/sdcard/kidslock/`（`*.txt`），设置 → 题库 → 重新加载。
- SMB 同步：设置 → 题库 → SMB 同步设置（host / share / user / pass）→ 立即更新。
- 内置题库只读，外部题库只能加题、不能改内置题。

---

## 常见问题

- **更新后无障碍变红/未开启？** 小米电视覆盖安装会自动解绑无障碍，点「去系统里打开无障碍」重开一次；安装器也会尝试重开。
- **锁屏时按主页键能回桌面？** 点「主页键拦截失效？点此修复」：把无障碍关掉再打开；仍不行确认悬浮层是「已开启」。
- **自启动显示「未确认」？** 系统不提供查询，重启一次电视后再看：绿色=已启，红色=没启。
- **手机扫码打不开？** 确认同一 WiFi（不是访客网络）、直接输验证页地址、路由器没开 AP 隔离。
- **设置页按菜单键没反应？** 正常：设置页不响应菜单键；只有锁屏答题页/壁纸页长按才用菜单键。
- **忘记家长密码？** 默认 `1234`；改过又忘了只能卸载重装（会清空设置）。
- **卸载**：设置 → 应用 → 每日壁纸 → 卸载；或家长设置 → 帮助 → 还原并卸载。

---

## 版本

- 当前：**v1.9.21**（versionCode 110）
- APK：4,413,934 字节，MD5 `ed05dcdc08736ca0ece24c1fde417a86`
- 更新日志见 Releases 页面：https://github.com/txhtxh11/txhlock/releases

## 开发者

- 源码：`app/`（Java，minSdk 18，targetSdk 33，compileSdk 34）
- 构建：

```powershell
$env:JAVA_HOME="C:\path\to\jdk17"
$env:ANDROID_HOME="C:\path\to\android-sdk"
.\gradlew assembleRelease
```

- 安装器：`tools/install.ps1`（PowerShell，自动找 adb / 扫电视 / 装 APK / 开无障碍）
- 手机设置页：应用内 `MiniWeb`（端口 8765，局域网 + 家长密码）

> 仅用于家长管理自己的电视；请勿用于破解、监控他人设备。
