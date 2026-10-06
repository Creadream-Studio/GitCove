package com.gitcove.app.data.store

import android.content.Context
import com.gitcove.app.domain.model.Repository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class RepoDb(
    val nextId: Long = 1L,
    val repos: List<Repository> = emptyList()
)

/**
 * 仓库元数据存储。
 * MVP 阶段以 JSON 文件持久化（kotlinx.serialization），
 * 后续按文档要求迁移到 Room（接口保持不变）。
 */
class RepoStore(context: Context) {

    private val file = File(context.filesDir, "repos.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun list(): List<Repository> = mutex.withLock { load().repos.sortedByDescending { it.addedAt } }

    suspend fun get(id: Long): Repository? = mutex.withLock { load().repos.firstOrNull { it.id == id } }

    suspend fun add(name: String, path: String, remoteUrl: String?): Repository = mutex.withLock {
        val db = load()
        val repo = Repository(
            id = db.nextId,
            name = name,
            path = path,
            remoteUrl = remoteUrl,
            currentBranch = ""
        )
        save(db.copy(nextId = db.nextId + 1, repos = db.repos + repo))
        repo
    }

    suspend fun remove(id: Long) = mutex.withLock {
        val db = load()
        save(db.copy(repos = db.repos.filterNot { it.id == id }))
    }

    suspend fun updateCurrentBranch(id: Long, branch: String) = mutex.withLock {
        val db = load()
        save(db.copy(repos = db.repos.map { if (it.id == id) it.copy(currentBranch = branch) else it }))
    }

    suspend fun updateRemote(id: Long, remoteUrl: String?) = mutex.withLock {
        val db = load()
        save(db.copy(repos = db.repos.map { if (it.id == id) it.copy(remoteUrl = remoteUrl) else it }))
    }

    suspend fun updateLastSync(id: Long, time: Long) = mutex.withLock {
        val db = load()
        save(db.copy(repos = db.repos.map { if (it.id == id) it.copy(lastSync = time) else it }))
    }

    private fun load(): RepoDb = runCatching {
        if (file.exists()) json.decodeFromString<RepoDb>(file.readText()) else RepoDb()
    }.getOrDefault(RepoDb())

    private fun save(db: RepoDb) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(db))
    }
}
