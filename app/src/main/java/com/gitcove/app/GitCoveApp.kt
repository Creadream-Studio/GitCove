package com.gitcove.app

import android.app.Application
import com.gitcove.app.data.git.SshTransport
import com.gitcove.app.data.log.CrashCapture
import com.gitcove.app.di.AppContainer
import com.gitcove.app.util.AppDirs
import com.gitcove.app.util.SampleRepoInstaller
import com.gitcove.app.work.SyncWorker
import kotlin.concurrent.thread

class GitCoveApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // v0.4.8 目录迁移：仓库根 / 操作日志 / 崩溃堆栈迁往外部应用专属目录
        // （/storage/emulated/0/Android/data/<包名>/files/）。搬移旧数据量可能不小
        // （整仓库 rename），放后台线程，不阻塞首帧；失败不致命（旧目录照常可用）。
        thread(name = "dirs-migrate", isDaemon = true) {
            runCatching {
                val moved = AppDirs.migrate(this) { oldPrefix, newPrefix ->
                    // suspend 函数在无协程上下文的线程里直接用 runBlocking 兑付
                    kotlinx.coroutines.runBlocking {
                        container.repoStore.migrateRepoPaths(oldPrefix, newPrefix)
                    }
                }
                if (moved > 0) container.opLog.append("migrate", "Dir migration: $moved item(s) → external app dir")
            }
        }
        // 内置示例仓库：首次启动安装到默认仓库根（新用户点进去就能体验各功能；可像普通仓库一样删除）
        thread(name = "sample-repo", isDaemon = true) {
            runCatching {
                SampleRepoInstaller.installIfNeeded(this@GitCoveApp, container.repoStore, container.opLog)
            }
        }
        // 崩溃捕获：魔改 ROM（电视/盒子等阉割手机系统）闪退难复现，
        // 先落盘完整堆栈（设置 → 日志可见），再交还系统默认处理
        CrashCapture.install(this, container.opLog)
        // SSH 传输层：注册应用内密钥目录（功能 45）
        SshTransport.setup(container.auth)
        // 后台自动同步（功能 20）
        if (container.auth.autoSyncEnabled) {
            SyncWorker.schedule(this, container.auth.autoSyncIntervalMin)
        }
        // 自动更新：开关开启时每次启动自动检查 GitHub 最新正式 Release
        // （检查结果在 UpdateChecker.state，主 Activity 消费后弹出更新弹窗）
        container.updateChecker.checkOnLaunch()
    }
}
