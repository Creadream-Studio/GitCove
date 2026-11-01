package com.gitcove.app

import android.app.Application
import com.gitcove.app.data.git.SshTransport
import com.gitcove.app.data.log.CrashCapture
import com.gitcove.app.di.AppContainer
import com.gitcove.app.work.SyncWorker

class GitCoveApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 崩溃捕获：魔改 ROM（电视/盒子等阉割手机系统）闪退难复现，
        // 先落盘完整堆栈（设置 → 日志可见），再交还系统默认处理
        CrashCapture.install(this, container.opLog)
        // SSH 传输层：注册应用内密钥目录（功能 45）
        SshTransport.setup(container.auth)
        // 后台自动同步（功能 20）
        if (container.auth.autoSyncEnabled) {
            SyncWorker.schedule(this, container.auth.autoSyncIntervalMin)
        }
    }
}
