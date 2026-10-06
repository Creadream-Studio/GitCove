package com.gitcove.app

import android.app.Application
import com.gitcove.app.data.git.SshTransport
import com.gitcove.app.di.AppContainer
import com.gitcove.app.work.SyncWorker

class GitCoveApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // SSH 传输层：注册应用内密钥目录（功能 45）
        SshTransport.setup(container.auth)
        // 后台自动同步（功能 20）
        if (container.auth.autoSyncEnabled) {
            SyncWorker.schedule(this, container.auth.autoSyncIntervalMin)
        }
    }
}
