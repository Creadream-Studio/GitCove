package com.gitcove.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gitcove.app.GitCoveApp
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 后台自动同步（功能 18/19/20/21）：
 * 周期性对所有带远端的仓库执行 Fetch + Pull（快进合并），
 * 冲突与失败写入操作日志，不中断后续仓库。
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? GitCoveApp ?: return Result.failure()
        val c = app.container
        var synced = 0
        for (repo in c.repoStore.list()) {
            val remote = repo.remoteUrl ?: continue
            val dir = File(repo.path)
            if (!dir.exists()) continue
            runCatching { c.gitCore.fetch(dir) }
                .onFailure { c.opLog.append("auto-sync", "${repo.name} fetch: ${it.message}") }
            runCatching { c.gitCore.pull(dir) }
                .onSuccess {
                    c.repoStore.updateLastSync(repo.id, System.currentTimeMillis())
                    synced++
                }
                .onFailure { c.opLog.append("auto-sync", "${repo.name} pull: ${it.message}") }
        }
        c.opLog.append("auto-sync", "自动同步完成，成功 $synced 个仓库")
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "gitcove_auto_sync"

        fun schedule(context: Context, intervalMin: Int) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMin.coerceAtLeast(15).toLong(), TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
