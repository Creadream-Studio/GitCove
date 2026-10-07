# GitCove（码湾）

> Android 端最好用的图形化 Git 客户端 —— 紧凑、美观、免费、无广告。对标 iOS 的 Working Copy。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2026%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)

**码湾 GitCove** 是一款纯 Kotlin + Jetpack Compose 打造的图形化 Git 客户端，基于 JGit 内核，无需 Root、无需命令行，开箱即用。

## ✨ 核心特性

- 🚀 **仓库管理**：HTTPS / SSH 克隆、新建仓库、导入本地目录、多远端管理
- 📝 **日常提交**：文件级暂存/取消暂存、提交信息历史参考、Amend、离线提交、提交后立即推送
- 🔄 **同步**：Fetch / Merge / Pull / Push、强制推送、批量操作、后台自动同步（WorkManager）
- 🌿 **分支**：切换/新建/删除/重命名、远程分支跟踪、Merge、标签（轻量与附注）、Stash 储藏
- 🕘 **历史**：仓库/文件级提交历史、提交图、Revert 撤销、旧版本签出
- ⚔️ **冲突解决**：按文件"采用我方/采用对方/手动编辑"三路解决
- 🔐 **认证**：GitHub/GitLab/Gitea 访问令牌、SSH 密钥生成与导入（RSA/ECDSA/Ed25519 导入）
- 👀 **查看**：差异对比（增删行高亮）、文件编辑器、文件名/内容搜索、GitHub 一键 PR
- 🎨 **紧凑工具风**：高信息密度、小圆角、Material Design 3 动态明暗主题

## 📦 下载

前往 [Releases](https://github.com/Creadream-Studio/GitCove/releases) 页面下载 `GitCove-x.x.x-universal.apk`。
该 APK 为**全架构通用包**（arm64-v8a / armeabi-v7a / x86 / x86_64 通用），纯 Java 实现无 native 库。

## 🔨 从源码构建

```bash
git clone https://github.com/Creadream-Studio/GitCove.git
cd GitCove
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

如需签名 release 包，在项目根目录创建 `keystore.properties`（勿提交）：

```properties
storeFile=gitcove-release.jks
storePassword=你的密码
keyAlias=gitcove
keyPassword=你的密码
```

未配置签名时 release 包为 unsigned；调试可直接 `./gradlew assembleDebug`。

## 🏗️ 技术栈

| 层级 | 选型 |
|------|------|
| 语言 | Kotlin 100% |
| UI | Jetpack Compose + Material Design 3（紧凑型工具风定制）|
| 架构 | MVVM + Repository + UseCase |
| 异步 | Kotlin Coroutines + Flow |
| Git 内核 | JGit 5.13（纯 Java，无需 NDK，天然全架构）* |
| SSH | JSch（mwiede 维护版，支持 OpenSSH/Ed25519）|
| 网络 | OkHttp（GitHub REST API v3）|
| 后台 | WorkManager 定期自动同步 |
| 测试 | JUnit + MockK + Turbine |

## 🚀 手动发布（GitHub Actions）

仓库内置手动发布工作流：`.github/workflows/release-apk.yml`

1. GitHub 仓库页 → **Actions** → **Release APK (手动)** → **Run workflow**
2. 可选填 Release 标签（默认自动取 `versionName`，如 `v1.0.2`）
3. 构建签名 Release APK 并自动发布到 **Releases** 页面

签名：在仓库 **Settings → Secrets → Actions** 配置 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 后，每次发布签名一致，可覆盖安装升级；未配置时 CI 会生成临时 keystore 并作为 artifact 上传，下载后可转为 secrets 固定。

\* 为什么是 JGit 5.13 而不是 6.x：JGit 6.x 编译目标为 Java 11，内部调用 `InputStream.readNBytes` / `readAllBytes` / `transferTo`、`String.strip` 等 API，这些 API 在 Android API 32 及以下不存在（core library desugaring 也不覆盖），运行时必抛 `NoSuchMethodError` —— 这正是 v1.0.1 在创建/克隆仓库后闪退的根因。5.13 是最后一个以 Java 8 编译的版本，字节码完全兼容 Android API 26+。

## 🗺️ 路线图

MVP 已覆盖文档 80 项功能清单中的核心项。后续计划：

- [ ] 按块暂存（hunk-level staging）
- [ ] 分栏对比与图片对比（Split / Color / Cut）
- [ ] Room 数据库迁移（仓库元数据与操作日志）
- [ ] libgit2 + JNI 内核评估（性能敏感场景）
- [ ] 内部空间公开挂载（DocumentProvider，供 MT 管理器等访问）
- [ ] WebDAV 服务器、Tasker 自动化集成
- [ ] Markdown / HTML / CSV 预览、代码高亮（Prism4j）
- [ ] Android Keystore / StrongBox 密钥硬件保护
- [ ] GPG / SSH 签名提交
- [ ] 数据备份与恢复

完整 80 项功能清单见开发文档。

## 📄 开源协议

本项目基于 [Apache License 2.0](LICENSE) 开源。

第三方组件：JGit（EPL 2.0）、JSch (mwiede fork, BSD-3)、OkHttp（Apache 2.0）等，商用请留意各自协议。
