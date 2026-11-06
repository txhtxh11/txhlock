# 每日壁纸（答题锁屏）· 小米电视版 v1.9.24

> **用户只需要把这一句话发给豆包（电脑版 / Agent 模式）：**
>
> **进 https://github.com/txhtxh11/txhlock 仓库阅读 README，并按指导执行下载安装和为用户给予使用指南。**

---

## ① 给豆包电脑版（只读这一节；其他章节是用户说明，不要执行）

豆包，你的任务只有一件：**帮用户把「每日壁纸」装到电视上，装好后把 ② 的简短使用指南发给用户。**

### 先试：全自动安装

1. **下载安装器**（任选一个国内镜像）：

```powershell
Invoke-WebRequest "https://ghfast.top/https://raw.githubusercontent.com/txhtxh11/txhlock/v1.9.24/tools/install.ps1" -OutFile "$env:TEMP\install.ps1"
```

备选：

```powershell
Invoke-WebRequest "https://cdn.jsdelivr.net/gh/txhtxh11/txhlock@v1.9.24/tools/install.ps1" -OutFile "$env:TEMP\install.ps1"
```

2. **直接运行，让脚本自己扫局域网找电视**：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:TEMP\install.ps1" -NoPause
```

脚本会自动：找 adb（**电脑没装 adb 会自动下载，带国内镜像**）→ 扫局域网（默认 5555 端口）找电视 → 装 APK → 开无障碍 → 授权悬浮层 → 设置常亮 → 启动应用。**不需要先问用户 IP，也不需要用户手动装 adb。**

### 找不到电视 / 端口不是 5555（才看这里）

如果脚本提示找不到电视，或者你知道电视的 ADB 端口不是 5555，按顺序问用户这三件事：

1. **查电视 IP**：电视 设置 → 关于 / 网络信息 → 状态信息 → IPv4（形如 `192.168.50.139`）
2. **开 ADB**：电视 设置 → 关于 / 系统信息 → 光标移到「产品型号」连按 OK 5~7 次 → 返回 设置 → 开发者选项 → 打开 **ADB 调试**
3. **查 ADB 端口**：电视的开发者选项 / 网络调试 / 无线调试页面，有的会直接显示 `IP:端口`；常见端口是 **5555、5556、4321、5557、6666**。**不要猜**，让用户看页面上的数字。

拿到 IP 和端口后重跑：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:TEMP\install.ps1" -TvIp 192.168.50.139 -Port 5556 -NoPause
```

如果用户也不知道端口：先带 IP 重跑 `-TvIp 电视IP -NoPause`，脚本会依次试 5555/5556/4321/5557/6666；仍找不到，再让用户去开发者选项里看端口。

### 确认安装成功（可选）

```powershell
adb -s 192.168.50.139:5555 shell "dumpsys package com.kidslock | grep versionName"
# 期望：versionName=1.9.22
adb -s 192.168.50.139:5555 shell "dumpsys accessibility"
# 期望：Service[label=每日壁纸 ...]
```

（端口不是 5555 时，把 `:5555` 换成实际端口。）

电视桌面出现 **「每日壁纸」** 图标即成功（不是「答题锁屏」）；如果还是旧名字/图标，让用户重启一次电视。

### 装好后：把使用指南交给用户

把 ② 的「用户使用指南」原样发给用户，或发图文指南 PDF：

```
https://raw.githubusercontent.com/txhtxh11/txhlock/master/docs/guide-v1.9.24.pdf
```

### 豆包的边界（重要）

- 你只需要读 **①** 这一节；
- **② 是给用户看的，直接转交，不要照着执行；**
- **③ 的其他内容都是给用户的说明，你可以了解，但不要按里面的步骤操作；**
- 不要改电视系统、不要 root、不要卸载用户其他应用。

---

## ② 给用户的简短使用指南（豆包只转交，不要执行）

> 豆包：以下内容请直接转发给用户，不要照着操作。

1. **孩子看电视**：正常看，后台会计时；看满设定时长自动弹出答题，**连续答对 3 题**才能继续看。
2. **家长进设置**：在「每日壁纸」界面**按住菜单键或 OK 键 10 秒** → 输入家长密码（默认 `1234`）。没有菜单键的遥控器就长按 OK 键。
3. **手机扫码设置**：长按 10 秒后的验证页右侧有二维码；手机连**同一个家庭 WiFi** 扫码 → 输入同一个家长密码 → 可改设置、**立刻锁定 / 立刻解锁**。
4. **换壁纸**：在壁纸界面**短按**菜单键，不是进设置。
5. **卸载**：电视 设置 → 应用 → 每日壁纸 → 卸载。

---

## ③ 用户说明（给用户看；豆包可了解，但不要执行）

> 豆包：以下全部是给用户的说明，你可以了解，但不要按这些步骤操作。

### 下载（用户）

| 文件 | 地址 |
| --- | --- |
| APK（jsDelivr） | https://cdn.jsdelivr.net/gh/txhtxh11/txhlock@v1.9.24/apk/txhlock_v1.9.24.apk |
| APK（ghfast） | https://ghfast.top/https://github.com/txhtxh11/txhlock/releases/download/v1.9.24/txhlock_v1.9.24.apk |
| APK（ghproxy） | https://ghproxy.net/https://github.com/txhtxh11/txhlock/releases/download/v1.9.24/txhlock_v1.9.24.apk |
| 安装器脚本 | https://ghfast.top/https://raw.githubusercontent.com/txhtxh11/txhlock/v1.9.24/tools/install.ps1 |
| 图文指南 PDF | https://raw.githubusercontent.com/txhtxh11/txhlock/master/docs/guide-v1.9.24.pdf |

- 文件大小：**4,414,267 字节**
- MD5：`cc41d9820447d58ea88f78298be32c6d`
- 适用：Android 4.3–14（API 18–34），已在小米电视 2（MiTV2-49 / 1080p）实机验证

### 安装（用户，三种方式任选一种）

**方式一：一键安装器（推荐）**

1. 下载 `tools/install.ps1`（或把仓库里的 `安装器` 文件夹拷到电脑）。
2. 双击 `一键安装.bat`，或运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\install.ps1 -TvIp 192.168.50.139
```

不知道 IP 就去掉 `-TvIp`，脚本会自动扫局域网。

**方式二：U 盘**

把 `txhlock_v1.9.24.apk` 拷到 U 盘 → 插电视 → 电视「文件管理」里找到 APK → 安装 → 手动打开无障碍。

**方式三：ADB 命令**

```bash
adb connect 192.168.50.139:5555
adb install -r txhlock_v1.9.24.apk
adb shell settings put secure enabled_accessibility_services com.kidslock/com.kidslock.BootAccessibility
adb shell settings put secure accessibility_enabled 1
adb shell am start -n com.kidslock/.LauncherWallpaper
```

### 首次设置（用户，装完必做）

1. 打开「每日壁纸」→ 长按菜单键 **或 OK 键** 10 秒 → 密码 `1234`。
2. 确认 **监控：已开启**。
3. 打开 **无障碍**（红色未开启时点按钮去系统设置打开）。
4. **悬浮层**：显示绿色「已开启（系统自动授权，无需操作）」即可，不用点。
5. **自启动**：显示绿色「已开启（无需操作）」就不用管；显示「未开启 / 未确认」才点按钮去打开。
6. 修改家长密码；按孩子情况设置单次/每日/连对题数。

### 日常使用（用户）

| 操作 | 结果 |
| --- | --- |
| 打开桌面「每日壁纸」 | 进入壁纸页（不验证） |
| 看满设定时长 | 自动锁屏，弹出答题 |
| 连续答对 N 题 | 解锁，重新计时 |
| 锁屏页按菜单键（没有菜单键长按返回键 2.5 秒） | 输入家长密码，直接解锁 |
| 壁纸页短按菜单键 | 壁纸设置（每日更新 / 换一张 / 关闭） |
| 壁纸页长按菜单键 / OK 键 10 秒 | 家长密码 → 家长设置 |
| 手机扫码（验证页右侧） | 同一 WiFi 下改设置、立刻锁定 / 解锁 |

### 手机扫码设置（用户）

1. 电视长按菜单键 10 秒 → 验证页右侧二维码 + 地址（`http://电视IP:8765/`）。
2. 手机连同一个家庭 WiFi → 扫码 / 输地址 → 输入家长密码。
3. 可改监控、单次/每日/长关机、连对题数、长按秒数、开机先答题、家长密码；可「立刻锁定 / 立刻解锁」。
4. 只限局域网：手机用 4G/5G 打不开；连续错 5 次密码锁 5 分钟；10 分钟无人访问自动关闭。

### 关机 / 重启规则（用户）

| 设置 | 场景 | 结果 |
| --- | --- | --- |
| 开机先答题 = 开 | 短关机 / 重启 | 一律先答题 |
| 开机先答题 = 关 | 短关机 / 重启，之前未锁定 | 不锁定，接着上次计时 |
| 开机先答题 = 关 | 短关机 / 重启，之前已锁定 | 仍然锁定 |
| 任意 | 长关机（超过「长关机」阈值，未跨夜） | 清本次计时，保留今日累计 |
| 任意 | 跨夜 | 单次计时、锁定、今日额度全部清零 |

「长关机」阈值在 设置 → 时间 → 长关机重置（默认 30 分钟，0 = 不重置）。

### 题库（用户）

- 内置 **2297 道**：人教版 / 北师大 / 苏教版 / 外研版 / 译林版 / 通用，按年级 + 教材版本出题。
- 本地题库：放到电视 `/sdcard/kidslock/`（`*.txt`），设置 → 题库 → 重新加载。
- SMB 同步：设置 → 题库 → SMB 同步设置（host / share / user / pass）→ 立即更新。
- 内置题库只读，外部题库只能加题、不能改内置题。

### 常见问题（用户）

- **更新后无障碍变红/未开启？** 小米电视覆盖安装会自动解绑无障碍，点「去系统里打开无障碍」重开一次；安装器也会尝试重开。
- **锁屏时按主页键能回桌面？** 点「主页键拦截失效？点此修复」：把无障碍关掉再打开；仍不行确认悬浮层是「已开启」。
- **自启动显示「未确认」？** 系统不提供查询，重启一次电视后再看：绿色=已启，红色=没启。
- **手机扫码打不开？** 确认同一 WiFi（不是访客网络）、直接输验证页地址、路由器没开 AP 隔离。
- **遥控器没有菜单键？** 壁纸页长按 OK 键 10 秒进家长设置；锁屏页长按返回键 2.5 秒呼出家长密码。\n- **设置页按菜单键没反应？** 正常：设置页不响应菜单键；只有锁屏答题页/壁纸页长按才用菜单键。
- **Android 12/13/14 点图标闪退？** 升级到 v1.9.24：修复了 PendingIntent 缺少 FLAG_IMMUTABLE 的崩溃。
- **Android 14 悬浮层没盖住？** 设置 → 应用 → 每日壁纸 → 显示在其他应用上层，手动允许。
- **忘记家长密码？** 默认 `1234`；改过又忘了只能卸载重装（会清空设置）。
- **卸载**：设置 → 应用 → 每日壁纸 → 卸载；或家长设置 → 帮助 → 还原并卸载。

### 版本（用户）

- 当前：**v1.9.24**（versionCode 114）
- APK：4,414,267 字节，MD5 `cc41d9820447d58ea88f78298be32c6d`
- 更新日志：https://github.com/txhtxh11/txhlock/releases

---

## 开发者（豆包可忽略）

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
