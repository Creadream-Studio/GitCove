# GitCove 码湾

Android 上的图形化 Git 客户端，免费、无广告。Kotlin + Jetpack Compose 写的，底层是 JGit，不需要 root，也不用敲命令行。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)

## 能做什么

- 克隆、新建、导入本地仓库，支持 HTTPS 和 SSH，多远端
- 勾选文件即暂存，取消勾选即取消暂存；Amend、离线提交，提交信息可参考历史，提交完可以直接推送
- Fetch / Merge / Pull / Push，强制推送也有，可以开后台自动同步
- 分支切换 / 新建 / 删除 / 重命名，标签（轻量、附注），Stash
- 提交历史带作者头像和提交图，能按单个文件看，也能 Revert、签出旧版本
- 冲突按文件解决：采用我方 / 采用对方 / 手动编辑
- GitHub 支持浏览器授权登录（PKCE），也可以用 GitHub / GitLab / Gitea 的访问令牌，Git 身份可以配多套
- SSH 密钥生成与导入（RSA / ECDSA / Ed25519）
- 差异对比（增删行高亮）、文件编辑器、文件名和内容搜索，GitHub 上一键开 PR
- MD2 工具风界面，信息密度高，明暗主题，五种强调色可换

## 下载

到 [Releases](https://github.com/Creadream-Studio/GitCove/releases) 下载 `GitCove-x.x.x-universal.apk`。这是全架构通用包（arm64-v8a / armeabi-v7a / x86 / x86_64），不含 native 库，Android 8.0 以上都能装。

## 自己编译

```bash
git clone https://github.com/Creadream-Studio/GitCove.git
cd GitCove
./gradlew assembleRelease
# 产物在 app/build/outputs/apk/release/
```

要出签名的 release 包，先在项目根目录建一个 `keystore.properties`（别提交上去）：

```properties
storeFile=gitcove-release.jks
storePassword=你的密码
keyAlias=gitcove
keyPassword=你的密码
```

不配置的话 release 包是 unsigned。日常调试直接 `./gradlew assembleDebug`。

## 技术栈

| | |
|---|---|
| UI | Jetpack Compose + Material Design 2（紧凑工具风定制）|
| 架构 | MVVM + Repository + UseCase |
| Git | JGit 5.13，纯 Java 实现，无 NDK |
| SSH | JSch（mwiede 维护版，支持 OpenSSH / Ed25519）|
| 网络 | OkHttp（GitHub REST API v3）|
| 后台 | WorkManager 定期自动同步 |
| 测试 | JUnit + MockK + Turbine |

JGit 为什么停在 5.13 不上 6.x：6.x 编译目标是 Java 11，内部用了 `InputStream.readNBytes`、`String.strip` 这些 API，Android API 32 及以下没有，core library desugaring 也不覆盖，运行时直接 `NoSuchMethodError`。v1.0.1 创建仓库就闪退，根因就是这个。5.13 是最后一个 Java 8 字节码的版本，API 26+ 上跑得稳。

## 发布

仓库里有手动发布工作流 `.github/workflows/release-apk.yml`：仓库页 → Actions → Release APK → Run workflow，标签可以不填（默认取 `versionName`），构建完自动发到 Releases。

要让用户能覆盖安装升级，每次构建的签名必须一致：把 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 配到仓库 Settings → Secrets → Actions。没配的时候 CI 会拿临时 keystore 出包，产物作为 artifact 上传，下载后转成 secrets 固定下来即可。

## 接下来想做的

- 按块暂存（hunk 级别）
- 分栏对比、图片对比
- GPG / SSH 签名提交
- 数据备份与恢复
- Markdown / 代码高亮预览
- Android Keystore 密钥硬件保护

## 协议

Apache License 2.0。第三方组件：JGit（EPL 2.0）、JSch（BSD-3）、OkHttp（Apache 2.0），商用前各自留意协议。
