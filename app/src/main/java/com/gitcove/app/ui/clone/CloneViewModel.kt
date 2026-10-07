package com.gitcove.app.ui.clone

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitcove.app.data.git.GitCredentials
import com.gitcove.app.data.remote.GitHubApi
import com.gitcove.app.data.remote.GitHubRepoInfo
import com.gitcove.app.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 克隆 / 新建 / 导入仓库（功能 1/2/3/4）
 */
class CloneViewModel(private val c: AppContainer) : ViewModel() {

    var busy by mutableStateOf(false)
        private set
    var progress by mutableStateOf("")
        private set
    var githubRepos by mutableStateOf<List<GitHubRepoInfo>>(emptyList())
        private set

    private val msg = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = msg

    /**
     * 完成回调（含 nav.popBackStack 等导航操作）必须回到主线程执行：
     * NavController 非线程安全，在 IO 线程调用会与 Compose 重组竞争导致闪退。
     */
    private suspend fun notifyDone(onDone: () -> Unit) {
        withContext(Dispatchers.Main.immediate) { onDone() }
    }

    /** 功能 1/2：从远程克隆 */
    fun cloneRepo(url: String, name: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            progress = "准备克隆…"
            try {
                val cleanUrl = url.trim()
                require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") ||
                    cleanUrl.startsWith("ssh://") || cleanUrl.startsWith("git@") ||
                    cleanUrl.startsWith("git://")) { "不支持的协议，请使用 https / ssh / git 地址" }
                val repoName = name.trim().ifBlank { GitCredentials.repoNameOf(cleanUrl) }
                require(repoName.isNotBlank()) { "请填写仓库名称" }
                val dir = File(c.repoParent, repoName)
                require(!(dir.exists() && dir.listFiles()?.isNotEmpty() == true)) { "已存在同名仓库：$repoName" }
                dir.mkdirs()
                c.gitCore.clone(cleanUrl, dir) { progress = it }.getOrThrow()
                val repo = c.repoStore.add(repoName, dir.absolutePath, cleanUrl)
                c.gitCore.status(dir).onSuccess { c.repoStore.updateCurrentBranch(repo.id, it.branch) }
                c.repoStore.updateLastSync(repo.id, System.currentTimeMillis())
                c.opLog.append("repo", "克隆仓库: $cleanUrl → $repoName")
                msg.tryEmit("克隆完成：$repoName")
                notifyDone(onDone)
            } catch (e: Throwable) {
                msg.tryEmit("克隆失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    /** 功能 4：创建新仓库 */
    fun initRepo(name: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            try {
                val repoName = name.trim()
                require(repoName.isNotBlank()) { "请填写仓库名称" }
                val dir = File(c.repoParent, repoName)
                require(!dir.exists()) { "已存在同名仓库：$repoName" }
                dir.mkdirs()
                c.gitCore.init(dir).getOrThrow()
                c.repoStore.add(repoName, dir.absolutePath, null)
                c.opLog.append("repo", "新建仓库: $repoName")
                msg.tryEmit("已创建新仓库：$repoName")
                notifyDone(onDone)
            } catch (e: Throwable) {
                msg.tryEmit("创建失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    /** 功能 3/5：导入本地目录 / 关联外部目录 */
    fun importRepo(path: String, name: String?, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            busy = true
            try {
                val dir = File(path.trim())
                require(dir.exists() && dir.isDirectory) { "目录不存在：$path" }
                require(c.gitCore.isValidRepo(dir)) { "该目录不是有效的 Git 仓库（缺少 .git）" }
                val repoName = (name ?: dir.name).trim().ifBlank { dir.name }
                c.repoStore.add(repoName, dir.absolutePath, c.gitCore.remoteUrl(dir))
                c.opLog.append("repo", "导入仓库: $repoName ← ${dir.absolutePath}")
                msg.tryEmit("已导入仓库：$repoName")
                notifyDone(onDone)
            } catch (e: Throwable) {
                msg.tryEmit("导入失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    /** 功能 2：从我的 GitHub 选择仓库 */
    fun loadMyGithubRepos() {
        viewModelScope.launch(Dispatchers.IO) {
            c.githubApi.listUserRepos()
                .onSuccess { githubRepos = it }
                .onFailure { msg.tryEmit(it.message ?: "获取仓库列表失败") }
        }
    }

    fun dismissGithubList() { githubRepos = emptyList() }
}
