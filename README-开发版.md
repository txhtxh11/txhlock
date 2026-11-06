# 答题锁屏 2.0 开发版

从正式的 **v1.9.34** 复制出来的独立开发目录，不改生产仓库，用于开发：

1. **错题解析 + 错题重练**
2. **应用内更新**

## 当前版本

- `versionName = 2.0.0-dev`
- `versionCode = 200`
- APK：`apk/txhlock_v2.0.0-dev.apk`

## 错题解析重练

- 答错后，题目下方显示「解析：正确答案是……」
- 错题会写进 `files/wrong_book.json`，最多保留 100 道
- 下次出题优先出错题；答对后自动移出
- 错题重练是「同题再答」，不是换一道同知识点的题
- 解析按题型生成（数学/拼音/古诗/英语/语文/理化生/史地政），不含 2297 道逐题人工解析

## 应用内更新

- 家长设置底部新增「检查更新」
- 检查 `update.json`，有新版本则下载 APK、校验 MD5、拉起系统安装器
- Android 7+ 使用 `UpdateProvider` 的 `content://` URI
- Android 8+ 会先引导开启「安装未知应用」
- 最后一步仍需用户在系统安装器点「安装」，不是静默安装

### update.json 放哪

`update.json` 现在指向 `dev` 分支：

```
https://cdn.jsdelivr.net/gh/txhtxh11/txhlock@dev/apk/txhlock_v2.0.0-dev.apk
```

如果要把这个开发版发出去测试，需要把 `dev` 分支和 `update.json`、`apk/txhlock_v2.0.0-dev.apk` 推到 GitHub；否则「检查更新」会提示下载失败。

以后每发一版：

1. 改 `app/build.gradle` 的 `versionCode/versionName`
2. 改 `app/src/main/java/com/kidslock/AppVer.java`
3. 重新打包
4. 更新 `update.json` 的 `versionCode/versionName/md5/urls`

## 构建

```powershell
$env:JAVA_HOME="C:\Users\txh11\android-dev\jdk\jdk-17.0.20.1+1"
$env:ANDROID_HOME="C:\Users\txh11\android-dev\sdk"
.\gradlew assembleRelease --offline
```

## 测试状态

- Android 14 Chromecast 盒子（238）已装 2.0.0-dev
- 错题解析、错题优先重练已实测
- 应用内更新的「检查更新」按钮已接入；因为 dev 分支还没推到 GitHub，下载链路还没做端到端实测
- 小米电视 139（4.3）、Sony 44（7.0）仍保持生产版 1.9.34，没有装开发版

> 这是开发目录，不要直接当正式版发布；确认后再合并回主仓库。
